package vsu.cs.kattsyn.chatbotv3;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class OntologyHandler {
    private final Model model;

    public OntologyHandler(String ontologyFilePath) {
        model = ModelFactory.createDefaultModel();
        InputStream in = FileManager.get().open(ontologyFilePath);
        if (in == null) {
            throw new IllegalArgumentException("File: " + ontologyFilePath + " not found");
        }
        model.read(in, null);
    }

    /**
     * Метод выдает ответ чат-бота. Создает объект запроса (Query), с помощью try-with-resources подключается к онтологии, передавая туда запрос.
     * Результаты запроса записывает в поле ResultSet results. Создает StringBuilder.
     * Далее у нас идет развилка. 1. запрос <животные>, который будет выводить информацию по всем животным. 2. запрос о каком-то индивиде, который выведет информацию по аттрибуту,
     * необходимый пользователю. Рассмотрим 1-й случай: Создается словарь Map<String, AnimalInfo> с животным и информации о нем соответственно. Далее собирается информация по каждому животному.
     * Создаются поля QuerySolution, и 3 RDFNode, с объектом, субъектом и предикатом. Создается объект класса AnimalInfo, куда и кладется нужная информация.
     * Тип, имя, и аттрибуты собираются по ключевым словам этих аттрибутов из проверяя содержится ли значение из attributeQueries в предикате.
     * Если нашли нужный, то записываем его во внутреннюю Map'у attributes класса AnimalInfo, где ключом будет являться первое ключевое слово из attributeQueries, а значением
     * значение полученное в результате запроса. И так для каждого животного. Затем в sb (stringBuilder) собираем строку вывода чат-бота, где перебираем toString() каждого AnimalInfo.
     * 2-й случай: похожим образом как для всех животных перебираем аттрибуты для конкретного нашего животного и затем собираем sb (stringBuilder), который, перебрав все аттрибуты, возвращаем из функции.
     *
     * @param sparqlQuery      уже сгенерированный SpaRQL запрос
     * @param userInput        ввод, который делал пользователь
     * @param animalQueries    словарь животных Map<Pair, String>, где Pair - класс, содержащий 2 строки. В первой ключевое слово, во второй название животного в И.п.
     * @param attributeQueries словарь аттрибутов, которые могут иметь животные. Map<String[], String>, где String[] - набор ключевых слов для предиката String.
     * @return возвращает ответ, сформированный чат-ботом
     */
    public String queryOntology(String sparqlQuery, String userInput, Map<Pair, String> animalQueries, Map<String[], String> attributeQueries) {
        Query query = QueryFactory.create(sparqlQuery);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet results = qexec.execSelect();
            StringBuilder sb = new StringBuilder();

            if (userInput.toLowerCase().contains("животные")) {
                Map<String, AnimalInfo> animals = new HashMap<>();

                while (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    RDFNode subjectNode = soln.get("subject");
                    RDFNode predicateNode = soln.get("predicate");
                    RDFNode objectNode = soln.get("object");

                    if (subjectNode != null && subjectNode.isURIResource()) {
                        String subject = subjectNode.asResource().getURI();
                        animals.putIfAbsent(subject, new AnimalInfo(subject));
                        AnimalInfo animalInfo = animals.get(subject);

                        if (predicateNode != null && objectNode != null) {
                            String predicate = predicateNode.toString();
                            for (String[] attributeKey : attributeQueries.keySet()) {
                                if (predicate.endsWith(attributeQueries.get(attributeKey))) {
                                    animalInfo.attributes.put(attributeKey[0], objectNode.asLiteral().getString());
                                }
                            }
                            if (predicate.endsWith("#type")) {
                                String type = objectNode.asResource().getLocalName();
                                animalInfo.addType(type);
                            }

                            for (Pair key : animalQueries.keySet()) {
                                if (subject.contains(animalQueries.get(key))) {
                                    animalInfo.individual = key.getSecond();
                                }
                            }
                        }
                    }
                }

                sb.append("Животные:\n");
                for (AnimalInfo animalInfo : animals.values()) {
                    sb.append(animalInfo).append("\n");
                }
                return sb.toString();
            } else {
                while (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    Map<RDFNode, String> nodesKeyMap = new HashMap<>();
                    //hasAge -> substring (3, length).toLowerCase
                    for (String[] key : attributeQueries.keySet()) {
                        //если начиная с третьего индекса, с маленькой буквы значение
                        //из набора аттрибутов имеется в строке результатов, которая выглядит
                        //примерно так: ( ?name = "Dixie" ) ( ?age = 6 )
                        String attr = attributeQueries.get(key).substring(3).toLowerCase();
                        if (soln.toString().contains(attr)) {
                            nodesKeyMap.put(soln.get(attr), key[0]);
                        }
                    }
                    for (RDFNode node : nodesKeyMap.keySet()) {
                        if (node != null) {
                            String curAttr = node.asLiteral().getString();
                            sb.append(nodesKeyMap.get(node).substring(0, 1).toUpperCase())
                                    .append(nodesKeyMap.get(node).substring(1))
                                    .append(": ")
                                    .append(curAttr)
                                    .append("\n");
                        }
                    }
                }
                return !sb.isEmpty() ? sb.toString() : "Нет данных по запросу.";
            }
        }
    }

    /**
     * Внутренний класс, содержащий информацию о животном
     */
    private static class AnimalInfo {
        String uri;
        String type;
        String individual;
        Map<String, String> attributes;

        AnimalInfo(String uri) {
            this.uri = uri;
            this.attributes = new HashMap<>();
        }

        void addType(String type) {
            if (type.contains("Mammal")) {
                this.type = "Млекопитающее";
            } else if (type.contains("Bird")) {
                this.type = "Птица";
            } else if (type.contains("Insect")) {
                this.type = "Насекомое";
            } else if (this.type == null && type.contains("Animal")) {
                this.type = "Животное";
            }
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder(type);
            stringBuilder.append(" ")
                    .append(individual);
            for (String key : attributes.keySet()) {
                stringBuilder.append(", ").append(key).append(": ").append(attributes.get(key));
            }
            return stringBuilder.toString();
        }
    }
}
