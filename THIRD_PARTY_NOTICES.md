# Third-party notices

LockApp bundles question data from the sources below. Everything else in this repository is LockApp's own code.

## General-knowledge questions: `app/src/main/assets/questions/gk.json`

Adapted from [Open Trivia DB](https://opentdb.com), © its contributors, licensed under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The questions were decoded, reformatted
and deduplicated. This file is shared under the same CC BY-SA 4.0 licence.

## SAT maths questions: the `SAT_MATH` entries in `app/src/main/assets/questions/sat.json`

From the [OpenSAT](https://github.com/Anas099X/OpenSAT) question database. OpenSAT's licence lets anyone use its
database in their own projects, including commercially. LockApp uses only the database, not OpenSAT's code or name.
Items were filtered by `tools/build_question_packs.py`:
- questions that rely on a figure or are too long were dropped
- questions whose answer key disagrees with their explanation, or with a re-computed answer, were dropped
- LaTeX was converted to plain text

## SAT vocabulary questions: the `SAT_WORDS` entries in `app/src/main/assets/questions/sat.json`

Built from [core-word-list](https://github.com/siddharthagit/core-word-list):

```
MIT License

Copyright (c) 2026 Siddhartha(Sidd) Bhattacharjee

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Downloaded on the phone at runtime (not stored in this repository)

- [The Trivia API](https://the-trivia-api.com), licensed CC BY-NC 4.0, used for personal, non-commercial top-ups.
- [Open Trivia DB](https://opentdb.com), as above.
