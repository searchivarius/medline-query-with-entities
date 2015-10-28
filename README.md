A self-sufficient Solr/Lucene 5.x query application for the index created using https://github.com/searchivarius/medline-index-with-entities

The index can be used as a regular SOLR instance. If one needs to use annotations, there is a special extension to do so. The query language is hard for humans to use. Therefore, there is a special simplified query language and an API to transform this simpler query language into the format that the extension can understand.

The query contains several terms that will be OR-ed together (you can also AND them if you wish). Alternatively, you can generate a query using the API, surround it with brackets, and combine this piece with other SOLR constructs.

Summary of term types:

1) Keyword, e.g., ESR1

2) Quoted phrase, e.g., "heart attack"

3) Concept query. It is a keyword or a quoted phrase followed by a pseudo-field name in brackets, e.g.:
esr1[gene]
Another example with quotes:
"heptanoic acid"[chemical]
Case doesn't matter. To query any gene, use the query *[gene]. Note that there should be an asterisk before the field name.

The list of pseudo fields is:Chemical , Disease, DNAMutation, Gene, ProteinMutation, SNP, Species

4) A concept id query. This is very similar to the concept query, but the field name should be suffixed with _id. For example, the following query:
5888[gene_id]
will search for concept IDs 5888, which are also annotated as genes.

I hope that I understood your explanation of concept_id queries correctly. If not, it can probably be fixed.

5) Note on complex names. Those need to be quoted. You also need escape brackets and quotes if they are a part of the name, e.g. (note the backslash):
"\\[Ca2+]i"

Again, a test application can be found at:

https://github.com/searchivarius/medline-index-with-entities/blob/master/src/main/java/edu/cmu/lti/oaqa/annographix/solr/SimpleQueryApp.java
