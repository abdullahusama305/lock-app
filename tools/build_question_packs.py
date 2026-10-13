#!/usr/bin/env python3
"""Builds LockApp's bundled question packs.

Writes app/src/main/assets/questions/gk.json and sat.json. Run it once and commit the JSON;
the app seeds these into its database on first launch so the gate works offline.

    python3 -I tools/build_question_packs.py \
        --opensat <opensat.json> --words <words.json> \
        --opentdb-cache <empty or cache dir> --out app/src/main/assets/questions

Sources (credited on the app's About screen):
  * OpenSAT question database, https://github.com/Anas099X/OpenSAT. The questions are free to
    use. Download: https://api.jsonsilo.com/public/942c3c3b-3a0c-4be3-81c2-12029def19f5
  * core-word-list (MIT), https://github.com/siddharthagit/core-word-list
  * Open Trivia DB (CC BY-SA 4.0), https://opentdb.com. Fetched here with a session token at
    one request per 5.5 s; raw pages are cached in --opentdb-cache so re-runs are offline.

Question ids must match QuestionIds.kt: "q:" + first 16 hex chars of
sha1(norm(prompt) + "|" + norm(correct)), where norm keeps only [a-z0-9] after NFKD.
"""
import argparse
import ast
import base64
import collections
import hashlib
import json
import random
import re
import sys
import time
import unicodedata
import urllib.request
from pathlib import Path

# ---------------------------------------------------------------- shared helpers

def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s).lower()
    return "".join(ch for ch in s if ("a" <= ch <= "z") or ("0" <= ch <= "9"))


def qid(prompt: str, correct: str) -> str:
    return "q:" + hashlib.sha1((norm(prompt) + "|" + norm(correct)).encode()).hexdigest()[:16]


def clean_text(s):
    if s is None:
        return None
    s = s.replace("\u00ad", "").replace("\r", "")
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip() or None


def question(source, category, difficulty, prompt, correct, wrong, passage=None, explanation=None):
    return {
        "id": qid(prompt, correct),
        "source": source,
        "category": category,
        "difficulty": difficulty,
        "passage": passage,
        "prompt": prompt,
        "correct": correct,
        "wrong": wrong,
        "explanation": explanation,
    }

# ---------------------------------------------------------------- LaTeX -> Unicode

class LatexError(Exception):
    pass


SUP = dict(zip("0123456789+-=()nxyi", "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿˣʸⁱ"))
SUB = dict(zip("0123456789+-=()", "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎"))
SYMBOLS = {
    "pi": "π", "theta": "θ", "alpha": "α", "beta": "β", "Delta": "Δ", "mu": "μ", "sigma": "σ",
    "le": "≤", "leq": "≤", "ge": "≥", "geq": "≥", "ne": "≠", "neq": "≠", "lt": "<", "gt": ">",
    "times": "×", "cdot": "·", "div": "÷", "pm": "±", "infty": "∞", "approx": "≈",
    "circ": "°", "degree": "°", "angle": "∠", "triangle": "△", "perp": "⊥", "parallel": "∥",
    "cong": "≅", "sim": "∼", "ldots": "…", "dots": "…", "cdots": "⋯",
    "sin": "sin", "cos": "cos", "tan": "tan", "log": "log", "ln": "ln",
    "left": "", "right": "", "displaystyle": "", "quad": " ", "qquad": " ",
}
TEXT_WRAPPERS = {"text", "mathrm", "textbf", "mathbf", "textit", "mathit", "operatorname", "textrm"}
FRACTIONS = {"frac", "dfrac", "tfrac"}
DOLLAR = "\x00D\x00"
LBRACE = "\x00L\x00"
RBRACE = "\x00R\x00"


def _read_group(s, i):
    """Reads a {...} group (or a single char) starting at s[i]; returns (content, next_index)."""
    while i < len(s) and s[i] == " ":
        i += 1
    if i >= len(s):
        raise LatexError("missing argument")
    if s[i] != "{":
        if s[i] == "\\":
            m = re.match(r"\\([A-Za-z]+)", s[i:])
            if m:
                return s[i:i + 1 + len(m.group(1))], i + 1 + len(m.group(1))
        return s[i], i + 1
    depth, j = 0, i
    while j < len(s):
        if s[j] == "{":
            depth += 1
        elif s[j] == "}":
            depth -= 1
            if depth == 0:
                return s[i + 1:j], j + 1
        j += 1
    raise LatexError("unbalanced braces")


def _wrap(s):
    return s if re.fullmatch(r"-?[\w.π√²³⁴⁵⁶⁷⁸⁹⁰¹]+", s) else f"({s})"


def _script(content, table, marker):
    if content and all(ch in table for ch in content):
        return "".join(table[ch] for ch in content)
    return f"{marker}({content})" if len(content) > 1 else f"{marker}{content}"


def _convert(s):
    out, i = [], 0
    while i < len(s):
        ch = s[i]
        if ch == "\\":
            m = re.match(r"\\([A-Za-z]+)", s[i:])
            if not m:
                nxt = s[i + 1] if i + 1 < len(s) else ""
                i += 2
                if nxt in (" ", ",", ";", ":", "\\"):
                    out.append(" ")
                elif nxt == "!":
                    pass
                elif nxt in ("%", "&", "#", "_"):
                    out.append(nxt)
                else:
                    raise LatexError(f"unknown escape \\{nxt}")
                continue
            name = m.group(1)
            i += 1 + len(name)
            if name in FRACTIONS:
                num, i = _read_group(s, i)
                den, i = _read_group(s, i)
                out.append(f"{_wrap(_convert(num))}/{_wrap(_convert(den))}")
            elif name == "sqrt":
                if i < len(s) and s[i] == "[":
                    raise LatexError("nth root")
                arg, i = _read_group(s, i)
                out.append("√" + _wrap(_convert(arg)))
            elif name in TEXT_WRAPPERS:
                arg, i = _read_group(s, i)
                out.append(_convert(arg))
            elif name == "overline":
                arg, i = _read_group(s, i)
                out.append("".join(c + "\u0305" for c in _convert(arg)))
            elif name in SYMBOLS:
                out.append(SYMBOLS[name])
            else:
                raise LatexError(f"unknown command \\{name}")
        elif ch in "^_":
            arg, i = _read_group(s, i + 1)
            conv = _convert(arg)
            out.append(_script(conv, SUP if ch == "^" else SUB, ch))
        elif ch in "{}":
            i += 1  # bare grouping braces
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def _html_script(m):
    table = SUP if m.group(1).lower() == "sup" else SUB
    content = m.group(2)
    if all(ch in table for ch in content):
        return "".join(table[ch] for ch in content)
    return ("^" if table is SUP else "_") + f"({content})"


def latex_to_unicode(s):
    """Converts the light LaTeX/HTML used in OpenSAT items into plain Unicode, or raises LatexError."""
    if s is None:
        return None
    s = re.sub(r"<(sup|sub)>(.*?)</\1>", _html_script, s, flags=re.I)
    s = re.sub(r"<br\s*/?>", "\n", s, flags=re.I)
    s = re.sub(r"</?(i|b|em|strong|u|span|p)\b[^>]*>", "", s, flags=re.I)
    if re.search(r"<[a-z/][^>]*>", s, re.I):
        raise LatexError("html")
    s = s.replace("\\$", DOLLAR).replace("\\{", LBRACE).replace("\\}", RBRACE)
    s = re.sub(r"\\[()\[\]]", "", s)  # \( \) \[ \] delimiters
    if "\\begin" in s or "\\hline" in s:
        raise LatexError("table/array")
    s = s.replace("$", "")  # remaining unescaped $ are math delimiters
    s = re.sub(r"\*([^*\s][^*]{0,40}?)\*", r"\1", s)  # markdown *italics*
    s = _convert(s)
    s = s.replace(DOLLAR, "$").replace(LBRACE, "{").replace(RBRACE, "}")
    if "\\" in s:
        raise LatexError("leftover backslash")
    return re.sub(r"[ \t]{2,}", " ", s).strip()

# ---------------------------------------------------------------- OpenSAT

FIGURE_RE = re.compile(
    r"\b(figure|graphs?|graphed|table|chart|scatterplot|diagram|underlined|histogram|dot plot|"
    r"box plot|bar graph|shown (above|below|in)|the image|plotted|xy-plane below)\b",
    re.I,
)
KNOWN_BAD = {"b81173a5"}  # answer key claims a quadratic with no real roots crosses the x-axis twice


def nullish(v):
    return None if v in (None, "null", "", "None") else v


def explanation_agrees(explanation, correct):
    """Many OpenSAT items are machine-written and some answer keys are wrong. Keep an item only
    when its worked explanation ends on the keyed answer."""
    return bool(explanation) and bool(norm(correct)) and norm(correct) in norm(explanation[-160:])


# --- re-computing the answer for the two most common templates

_ALLOWED_NODES = (ast.Expression, ast.BinOp, ast.UnaryOp, ast.Constant, ast.Name, ast.Load,
                  ast.Add, ast.Sub, ast.Mult, ast.Div, ast.Pow, ast.USub, ast.UAdd)


def _to_python(expr):
    """'3x² - 5x + 2' -> '3*x**2 - 5*x + 2'. Returns None for anything it can't handle safely."""
    e = expr.strip().replace("−", "-").replace("×", "*").replace("·", "*").replace("÷", "/")
    e = e.replace("²", "**2").replace("³", "**3").replace("^", "**")
    if re.search(r"[^0-9x+\-*/(). ]", e):
        return None
    e = re.sub(r"(\d|\))\s*(x|\()", r"\1*\2", e)
    e = re.sub(r"(x)\s*(\(|\d)", r"\1*\2", e)
    try:
        tree = ast.parse(e, mode="eval")
    except SyntaxError:
        return None
    if not all(isinstance(n, _ALLOWED_NODES) for n in ast.walk(tree)):
        return None
    if any(isinstance(n, ast.Name) and n.id != "x" for n in ast.walk(tree)):
        return None
    if any(isinstance(n, ast.BinOp) and isinstance(n.op, ast.Pow)
           and not (isinstance(n.right, ast.Constant) and abs(n.right.value) <= 6) for n in ast.walk(tree)):
        return None
    return compile(tree, "<expr>", "eval")


def _number(s):
    s = s.strip().replace(",", "").replace("$", "").replace("−", "-")
    m = re.fullmatch(r"(-?\d+(?:\.\d+)?)(?:/(\d+(?:\.\d+)?))?", s)
    if not m:
        return None
    value = float(m.group(1))
    return value / float(m.group(2)) if m.group(2) else value


def _evaluate(code, x):
    try:
        return eval(code, {"__builtins__": {}}, {"x": x})  # code is a whitelisted arithmetic AST
    except (ZeroDivisionError, OverflowError):
        return None


FUNCTION_VALUE = re.compile(
    r"f\(x\)\s*=\s*(.+?)(?:\.\s|\.$|,\s|\s+for\s).*?value of f\(\s*(-?[\d./]+)\s*\)", re.I | re.S)
SOLVE_FOR_X = re.compile(r"^If\s+([^=,]+?)\s*=\s*([^=,]+?),\s*what is the value of x\s*\?$", re.I)


def recompute_check(prompt, correct):
    """True/False when the keyed answer can be re-computed, None when the item doesn't fit."""
    key = _number(correct)
    if key is None:
        return None
    m = FUNCTION_VALUE.search(prompt)
    if m:
        code, x = _to_python(m.group(1)), _number(m.group(2))
        if code is None or x is None:
            return None
        value = _evaluate(code, x)
        return None if value is None else abs(value - key) < 1e-6 * max(1.0, abs(key))
    m = SOLVE_FOR_X.match(prompt.strip())
    if m:
        lhs, rhs = _to_python(m.group(1)), _to_python(m.group(2))
        if lhs is None or rhs is None:
            return None
        left, right = _evaluate(lhs, key), _evaluate(rhs, key)
        return None if left is None or right is None else abs(left - right) < 1e-6 * max(1.0, abs(right))
    return None


def build_sat(opensat_path, stats):
    """SAT math only: OpenSAT's reading/writing items are mostly machine-generated with
    ambiguous or wrong keys, so they are not used."""
    data = json.loads(Path(opensat_path).read_text())
    out = []
    for section, category, max_len in (("math", "SAT_MATH", 250),):
        for item in data[section]:
            q = item.get("question") or {}
            if item.get("id") in KNOWN_BAD:
                stats[section]["known bad"] += 1
                continue
            visuals = item.get("visuals") or {}
            if nullish(visuals.get("type")) or nullish(visuals.get("svg_content")):
                stats[section]["has visual"] += 1
                continue
            raw_passage, raw_prompt = nullish(q.get("paragraph")), nullish(q.get("question"))
            choices = q.get("choices") or {}
            letter = nullish(q.get("correct_answer"))
            if not raw_prompt or letter not in choices or len(choices) < 4:
                stats[section]["malformed"] += 1
                continue
            if FIGURE_RE.search(" ".join(filter(None, [raw_passage, raw_prompt]))):
                stats[section]["refers to figure/underline"] += 1
                continue
            try:
                passage = clean_text(latex_to_unicode(raw_passage))
                prompt = clean_text(latex_to_unicode(raw_prompt))
                conv = {k: clean_text(latex_to_unicode(nullish(v) or "")) for k, v in choices.items()}
            except LatexError:
                stats[section]["latex not convertible"] += 1
                continue
            if len((passage or "") + prompt) > max_len:
                stats[section]["too long"] += 1
                continue
            texts = [t for t in conv.values() if t]
            if len(texts) != len(conv) or len(set(map(norm, texts))) != len(texts):
                stats[section]["empty/duplicate choices"] += 1
                continue
            try:
                explanation = clean_text(latex_to_unicode(nullish(q.get("explanation"))))
            except LatexError:
                explanation = None
            correct = conv[letter]
            if not explanation_agrees(explanation, correct):
                stats[section]["explanation disagrees with key"] += 1
                continue
            check = recompute_check(prompt, correct)
            if check is False:
                stats[section]["re-computed answer differs from key"] += 1
                continue
            if check:
                stats[section]["answer re-computed and confirmed"] += 1
            if len(explanation) > 700:
                explanation = explanation[:697].rstrip() + "…"
            wrong = [t for k, t in sorted(conv.items()) if k != letter]
            difficulty = {"easy": 1, "medium": 2, "hard": 3}.get(str(item.get("difficulty", "")).lower(), 0)
            out.append(question("opensat", category, difficulty, prompt, correct, wrong, passage, explanation))
            stats[section]["kept"] += 1
    return out

# ---------------------------------------------------------------- SAT vocabulary

def build_vocab(words_path, stats):
    """Every word in the list (SAT- and GRE-tagged), two questions per word."""
    words = json.loads(Path(words_path).read_text())
    sat = [w for w in words if w.get("word") and w.get("definition")]
    by_pos = collections.defaultdict(list)
    for w in sat:
        by_pos[w.get("partOfSpeech", "")].append(w)
    out = []
    for w in sat:
        rng = random.Random("lockapp:" + w["word"])
        pool = [o for o in by_pos[w.get("partOfSpeech", "")] if o["word"] != w["word"]]
        if len(pool) < 3:
            pool = [o for o in sat if o["word"] != w["word"]]
        d = w.get("difficulty") or 3
        difficulty = 1 if d <= 2 else (2 if d == 3 else 3)
        pos = w.get("partOfSpeech", "")
        explanation = f"{w['word']} ({pos}): {w['definition']}"
        if w.get("example"):
            explanation += f" Example: “{w['example']}”"
        defs = rng.sample(pool, 3)
        out.append(question("core-word-list", "SAT_WORDS", difficulty,
                            f"What does “{w['word']}” ({pos}) mean?",
                            w["definition"], [o["definition"] for o in defs], explanation=explanation))
        others = rng.sample(pool, 3)
        out.append(question("core-word-list", "SAT_WORDS", difficulty,
                            f"Which word means: “{w['definition'].rstrip('.')}”?",
                            w["word"], [o["word"] for o in others], explanation=explanation))
    stats["vocab"]["words"] = len(sat)
    stats["vocab"]["kept"] = len(out)
    return out

# ---------------------------------------------------------------- Open Trivia DB

OPENTDB_CATEGORIES = (9, 17, 19, 22, 23)  # GK, Science & Nature, Maths, Geography, History
OPENTDB_DELAY = 5.5


def _get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "LockApp-pack-builder/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:  # 429 still carries a JSON body
        return json.loads(e.read().decode() or "{}")


def _b64(s):
    return base64.b64decode(s).decode("utf-8")


def fetch_opentdb(cache_dir):
    cache = Path(cache_dir)
    cache.mkdir(parents=True, exist_ok=True)
    token = None
    raw = []
    for cat in OPENTDB_CATEGORIES:
        page_file = cache / f"opentdb_{cat}.json"
        if page_file.exists():
            raw += json.loads(page_file.read_text())
            continue
        if token is None:
            token = _get_json("https://opentdb.com/api_token.php?command=request")["token"]
            time.sleep(OPENTDB_DELAY)
        items, amount = [], 50
        while True:
            data = _get_json(f"https://opentdb.com/api.php?amount={amount}&category={cat}"
                             f"&type=multiple&encode=base64&token={token}")
            time.sleep(OPENTDB_DELAY)
            code = data.get("response_code")
            if code == 0:
                items += data.get("results", [])
            elif code == 5:
                time.sleep(OPENTDB_DELAY)
            elif code in (1, 4):  # fewer than `amount` left for this token: drain with smaller pages
                if amount == 1:
                    break
                amount = 10 if amount == 50 else 1
            elif code == 3:
                token = _get_json("https://opentdb.com/api_token.php?command=request")["token"]
                time.sleep(OPENTDB_DELAY)
            else:
                raise RuntimeError(f"Open Trivia DB returned {data}")
        print(f"  opentdb category {cat}: {len(items)} questions", file=sys.stderr)
        page_file.write_text(json.dumps(items))
        raw += items
    return raw


def build_gk(cache_dir, stats):
    out = []
    for item in fetch_opentdb(cache_dir):
        prompt = clean_text(_b64(item["question"]))
        correct = clean_text(_b64(item["correct_answer"]))
        wrong = [clean_text(_b64(w)) for w in item["incorrect_answers"]]
        if not prompt or not correct or len(wrong) != 3 or not all(wrong):
            stats["gk"]["malformed"] += 1
            continue
        difficulty = {"easy": 1, "medium": 2, "hard": 3}.get(_b64(item["difficulty"]), 0)
        out.append(question("opentdb", "GK", difficulty, prompt, correct, wrong))
        stats["gk"]["kept"] += 1
    return out

# ---------------------------------------------------------------- main

def dedupe(items):
    seen, out = set(), []
    for q in items:
        if q["id"] not in seen:
            seen.add(q["id"])
            out.append(q)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--opensat", required=True)
    ap.add_argument("--words", required=True)
    ap.add_argument("--opentdb-cache", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    stats = collections.defaultdict(collections.Counter)
    sat = dedupe(build_sat(args.opensat, stats) + build_vocab(args.words, stats))
    gk = dedupe(build_gk(args.opentdb_cache, stats))

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "sat.json").write_text(json.dumps(sat, ensure_ascii=False, indent=0))
    (out / "gk.json").write_text(json.dumps(gk, ensure_ascii=False, indent=0))

    for section, counter in stats.items():
        print(f"{section}: " + ", ".join(f"{k}={v}" for k, v in counter.most_common()))
    for name, items in (("sat.json", sat), ("gk.json", gk)):
        by_cat = collections.Counter((q["category"], q["difficulty"]) for q in items)
        print(f"{name}: {len(items)} questions; " + ", ".join(f"{c}/d{d}={n}" for (c, d), n in sorted(by_cat.items())))


if __name__ == "__main__":
    main()
