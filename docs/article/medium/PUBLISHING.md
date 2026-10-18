# Publishing the article

Files: `article.md` (English) and `article.pt-BR.md` (Portuguese). Images are in `img/en` and `img/pt` (diagrams) and `img/en/code-N.png` (code as images).

## Medium

1. New story, paste the text. Medium keeps headings, bold, lists, quotes and code blocks (`~~~` or triple backticks become code blocks).
2. Medium has no tables: replace the metrics table with the picture or a list.
3. Upload each image where the `![...](img/...)` line is.
4. Pick one of the three title options at the top of the file and delete the others.

## LinkedIn

LinkedIn articles do not format code. Use the code images (`img/en/code-N.png`) instead of code blocks, and the diagrams as images. For a short post, use `linkedin-post.md`.

## Before publishing

The Dockerfile was not built on this machine (Docker daemon was off): run `docker build -t dsupport .` first.
