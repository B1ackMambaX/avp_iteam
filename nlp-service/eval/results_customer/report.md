# Примеры заказчика: результат прогона

## 1. ERR (ожидали True, получили False, вид бенефициара: org)

Текст: Оплата за третье лицо ООО «СтройЭлектроМонтажСервис» ИНН 7712345678 по счету № 123 от 18.10.2026 за аренду спецтехники по договору № 23/А от 05.03.2026. В том числе НДС 22%, 12 200,00 руб.

Бенефициар: ООО «СтройЭлектроМонтажСервис» | Тип договора: Платеж за третье лицо

Решение: SEMANTIC, score=0.769, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.769,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.833
}
```

## 2. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Оплата по договору комиссии № К-1289/45 от 20.04.2026 за реализованный товар по отчету комиссионера № 12 от 30.10.2026. Сумма 128 256-00, НДС не облагается

Бенефициар: - | Тип договора: Договор комиссии

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 3. ERR (ожидали True, получили False, вид бенефициара: person)

Текст: Оплата за обучение Соколова Артема Дмитриевича, 7 «Ъ» класс, по договору № 1234/26 от 01.09.2026 за ноябрь 2026 г. Без НДС

Бенефициар: Соколов Артем Дмитриевич | Тип договора: Платеж за третье лицо

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 4. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Перечисление выручки принципалу по агентскому договору № 123/1 от 17.12.2021 за период 03.10.2022–24.10.2022. Сумма 2 160 000-00, в т.ч. НДС (22%) 333 556-49

Бенефициар: - | Тип договора: Агентский договор

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 5. ERR (ожидали True, получили False, вид бенефициара: person)

Текст: Оплата по счету № 1234 от 15.07.2024 за туристические услуги: тур в ОАЭ, Дубай, 7 ночей, турист — Чернова Ксения Игоревна. В том числе НДС 5%, 22 654,00 руб.

Бенефициар: Чернова Ксения Игоревна | Тип договора: Платеж за третье лицо

Решение: SEMANTIC, score=0.690, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.69,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.79
}
```

## 6. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Обеспечительный депозит по агентскому соглашению № 987654/1 от 11.05.2013. Сумма 100 000 000-00, без налога (НДС)

Бенефициар: - | Тип договора: Гарантийное письмо

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 7. ERR (ожидали True, получили False, вид бенефициара: org)

Текст: Лизинговый платеж за ноябрь 2026 г. по договору лизинга № 69567-ТТТ-24-НО-Л от 17.09.2022 за ООО «Авто-Регион» ИНН 3278451234. В т.ч. НДС 22%, 59 508,20 руб.

Бенефициар: ООО «Авто-Регион» | Тип договора: Платеж за третье лицо

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 8. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Оплата страховых взносов по отчету агента 06/25 ОСАГО. Агентский договор № 12345/КРЫУ/2019 от 18.02.2019. Сумма 567 890-12, без налога (НДС)

Бенефициар: - | Тип договора: Агентский договор

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 9. ERR (ожидали True, получили False, вид бенефициара: person)

Текст: Гонорар адвоката Громовой Любови Викторовны согласно реестру № ТУ-154 за 01.02.2022. Перевод по зарплатному проекту. НДС не облагается

Бенефициар: Громова Любовь Викторовна (адвокат) | Тип договора: Платеж за третье лицо

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 10. ERR (ожидали True, получили False, вид бенефициара: org)

Текст: ЕНП. Налог на прибыль и пени по уточненной декларации за 2029 г. ООО «ТехноПром» ИНН 5616873539. Платеж за третье лицо

Бенефициар: ООО «ТехноПром» | Тип договора: Платеж за третье лицо

Решение: SEMANTIC, score=0.650, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.65,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.748
}
```

## 11. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Оплата по счету № FDK19032FK от 12.12.2024 за услугу проживания и агентский сбор. Сумма 23 456-00, в т.ч. НДС 56-78. Для ООО «РитейлСеть», филиал Уральский

Бенефициар: - | Тип договора: Агентский договор

Решение: SEMANTIC, score=0.709, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.709,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.81
}
```

## 12. ERR (ожидали True, получили False, вид бенефициара: person)

Текст: Оплата за организацию образовательного процесса за Кузнецову Полину Андреевну, 5 класс, по договору № 896/85 от 20.12.2012 за ИЮЛЬ 2016 г. Без НДС

Бенефициар: Кузнецова Полина Андреевна | Тип договора: Платеж за третье лицо

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 13. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Перечисление денежных средств агенту ИП Пряникова Зульфия Ароновка ИНН 879581231233 по Соглашению об использовании электронного сервиса для оплаты принципалам. НДС не облагается

Бенефициар: - | Тип договора: Агентский договор

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 14. ERR (ожидали True, получили False, вид бенефициара: ip)

Текст: Оплата по счету № 34 от 12.05.2019 по письму за ИП Коваль Е Б Сумма 567000.00 рублекй, в т.ч. НДС (22%) 24032.09 рублей

Бенефициар: ИП Коваль Е.Б. | Тип договора: Платеж за третье лицо

Решение: SEMANTIC, score=0.739, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.739,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.793
}
```

## 15. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Оплата по агентскому договору № НШ-53 от 21.03.2020 за оказание услуг по поиску покупателей. НДС не облагается

Бенефициар: - | Тип договора: Агентский договор

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 16. ERR (ожидали True, получили False, вид бенефициара: org)

Текст: Оплата за ООО «АУ Заблудился» ИНН 9834567543 договор № 567 от десятого августа деветысячи двадцатого года, счет № 654 от десятого августа деветысячи двадцатого года, за выполнение работы согласно распределительному письму. Сумма 123000,00, в НДС (22%) 85 295 р.

Бенефициар: ООО «АУ Заблудился» | Тип договора: Платеж за третье лицо

Решение: SEMANTIC, score=0.674, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.674,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.708
}
```

## 17. ERR (ожидали True, получили False, вид бенефициара: org)

Текст: Оплата по договору аренды № 4839-65 от 05.07.2014 за ноябрь 2059 г. за ООО Кошки против собак. НДС не облагается

Бенефициар: ООО Кошки против собак | Тип договора: -

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 18. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Оплата по счету № НЕК-567 от 3.9.26. Отчет комиссионера о продажах №НЕ-456. В том числе НДС 5%, 1 571,43 руб.

Бенефициар: - | Тип договора: Договор комиссии

Решение: SEMANTIC, score=0.692, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.692,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.744
}
```

## 19. ERR (ожидали True, получили False, вид бенефициара: ip)

Текст: Оплата задолженности по просроченным процентам по кредитному договору № RR563DG5 с ИП Гоглевой Евдокией Зековной

Бенефициар: ИП Гоглева Евдокия Зековна | Тип договора: Платеж за третье лицо

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```

## 20. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Оплата по договору поставки №65/47/52 от 09.07.2017 за песок в пользу ООО «Глубина 3000» через агента ООО «Металл» по агентскому договору № 10/2014 от 16.04.2018. В том числе НДС 22%, 18032 рублей 79 копеек

Бенефициар: - | Тип договора: Агентский договор

Решение: SEMANTIC, score=0.819, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.819,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.773
}
```

## 21. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Оплата по счету № ИВ-5332 от 10.02.2019 за авансовый платеж по договору комиссии на покупку оборудования. НДС не облагается

Бенефициар: - | Тип договора: Договор комиссии

Решение: SEMANTIC, score=0.747, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.747,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.789
}
```

## 22. ERR (ожидали True, получили False, вид бенефициара: org)

Текст: Оплата по договору страхования 7656Й/956/46587782/21 по счету от 12.10.2021 (за ООО «Группа Здоровых Кошек»). НДС не облагается

Бенефициар: ООО «Группа Здоровых Кошек» | Тип договора: Платеж за третье лицо

Решение: SEMANTIC, score=0.750, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.75,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.742
}
```

## 23. ERR (ожидали True, получили False, вид бенефициара: org)

Текст: Оплата по счету № 6870 от 07.08.2015. Аренда нежилых помещений по договору аренды № 4886А2 от 12.01.2012 за март 2021 г. (Оплата за ООО Улица ЗА). В том числе НДС 5%, 1387.30 рублей

Бенефициар: ООО "Улица ЗА" | Тип договора: Платеж за третье лицо

Решение: SEMANTIC, score=0.744, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: False

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "SEMANTIC",
 "score": 0.744,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": 0.804
}
```

## 24. OK (ожидали False, получили False, вид бенефициара: none)

Текст: Оплата по агентскому отчету №39932 за реализованный товар за май  (позиции согласно отчета) по агентскому договору № 123453. 139 995-00 руб. без НДС

Бенефициар: - | Тип договора: Агентский договор

Решение: GATED, score=0.000, threshold=0.500, overridden=False

Извлечённое имя: None | попадание: None

```json
{
 "settingsVersion": 9,
 "overridden": false,
 "threshold": 0.5,
 "decision": "GATED",
 "score": 0.0,
 "thirdParty": false,
 "extractedName": null,
 "negativeSimilarity": null
}
```


# Legacy baseline \b(за|в пользу)\b

 1 OK  exp=1 pred=1

 2 ERR exp=0 pred=1

 3 OK  exp=1 pred=1

 4 ERR exp=0 pred=1

 5 OK  exp=1 pred=1

 6 OK  exp=0 pred=0

 7 OK  exp=1 pred=1

 8 OK  exp=0 pred=0

 9 OK  exp=1 pred=1

10 OK  exp=1 pred=1

11 ERR exp=0 pred=1

12 OK  exp=1 pred=1

13 OK  exp=0 pred=0

14 OK  exp=1 pred=1

15 ERR exp=0 pred=1

16 OK  exp=1 pred=1

17 OK  exp=1 pred=1

18 OK  exp=0 pred=0

19 ERR exp=1 pred=0

20 ERR exp=0 pred=1

21 ERR exp=0 pred=1

22 OK  exp=1 pred=1

23 OK  exp=1 pred=1

24 ERR exp=0 pred=1


legacy total=24 tp=12 fp=7 fn=1 tn=4 correct=16
