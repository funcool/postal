all: protocol

protocol:
	mkdir -p dist/latest
	asciidoctor -a docinfo -a stylesheet! -o dist/latest/proto.html proto.adoc

github: protocol
	ghp-import -m "Generate documentation" -b gh-pages dist/
	git push origin gh-pages
