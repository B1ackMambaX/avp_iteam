from natasha import Segmenter, NewsEmbedding, NewsNERTagger, Doc

seg = Segmenter()
ner = NewsNERTagger(NewsEmbedding())
texts = {
    3: "Опл. обучения в интересах Крылова Дмитрия Анатольевича, дог. 12",
    28: "Превод за трете лицо Савельева Олега Геннадьевича",
    30: "Оплата за Зотову Т.М.",
}
for i, t in texts.items():
    d = Doc(t)
    d.segment(seg)
    d.tag_ner(ner)
    print(i, [(s.start, s.stop, s.type) for s in d.spans])