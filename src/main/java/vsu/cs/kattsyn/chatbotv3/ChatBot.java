package vsu.cs.kattsyn.chatbotv3;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ChatBot {
    private final OntologyHandler ontologyHandler;
    private Map<Pair, String> animalQueries;
    private Map<String[], String> attributeQueries;


    private static final String ONTOLOGY_LINK = "<http://www.semanticweb.org/kattsyn/ontologies/2024/6/untitled-ontology-7#";

    public ChatBot(String ontologyFilePath) {
        ontologyHandler = new OntologyHandler(ontologyFilePath);
        initializeQueries();
    }

    /**
     * В animalQueries хранятся Map<Pair, String>, где Pair - класс, который содержит только две строки,
     * может быть создан, но не могут быть изменены строки. В первой строке мы храним ключевое слово,
     * по которому будем определять слово, во второй строке это слово на русском в Именительном падеже,
     * он используется один раз, при запросе пользователя вывести всех животных. Значением этой Map<>
     * является имя индивида, которое будем искать или передавать в запросах.
     * В attributeQueries хранятся аттрибуты, которые есть у индивидов, по ключу массив ключевых слов,
     * по значению сам аттрибут, так, как он записан в онтологии.
     */
    private void initializeQueries() {

        animalQueries = new HashMap<>();
        animalQueries.put(new Pair("собак", "собака"), "Dog");
        animalQueries.put(new Pair("кошк", "кошка"), "Cat");
        animalQueries.put(new Pair("вороб", "воробей"), "Sparrow");
        // добавляем нового индивида дельфина
        animalQueries.put(new Pair("мурав", "муравей"), "Ant");

        attributeQueries = new HashMap<>();
        attributeQueries.put(new String[]{"возраст", "лет"}, "hasAge");
        attributeQueries.put(new String[]{"имя", "зовут"}, "hasName");
        // добавляем новый атрибут
        attributeQueries.put(new String[]{"цвет", "раскраск"}, "hasColor");
        attributeQueries.put(new String[]{"порода", "породы"}, "hasBreed");
    }

    /**
     * Метод start() имитирует работу чат-бота в консоли, где по очереди, начиная с пользователя,
     * задаются вопросы, затем чат-бот на них отвечает.
     */
    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Чат-бот: Привет! Чем могу помочь?");
        while (true) {
            System.out.print("Вы: ");
            String userInput = scanner.nextLine();
            if (userInput.equalsIgnoreCase("выход")) {
                System.out.println("Чат-бот: До свидания!");
                break;
            }
            String response = getResponse(userInput);
            System.out.println("Чат-бот: " + response);
        }
    }

    /**
     * Приводит к нижнему регистру запрос пользователя,
     * заводит строковые поля для индивида (String animal) и для аттрибутов, которые эти животные будут иметь (String attribute).
     * Перебирает ключи в словаре animalQueries на наличие ключевого слова, обозначающего нужное животное.
     * Например, если в запросе было слово кошка, то будет брать значение Cat, которое соответствует ключу кошки.
     * Присваивает полю animal значение из этого словаря.
     * Затем перебирает ключи аттрибутов, чтобы найти другое ключевое слово. Им будет являться аттрибут, который и нужен пользователю.
     * Например, при запросе <возраст кошки>, найдет сначала "кошки" из словаря животных, запомнит.
     * Затем из аттрибута найдет "возраст", затем значение по ключу "возраст" передаст полю attribute.
     * Наше поле attribute станет hasAge.
     * Затем исходя из того, какие поля есть у нас (!= null) вызываем соответствующий перегруженный метод generateSparqlQuery(),
     * который будет генерировать SpaRQL запрос, исходя из того, что хочет пользователь.
     * Например, если пользователь хочет возраст кошки, то вызовет метод generateSparqlQuery(String animal (#Cat), String attribute (hasAge))
     * Если только слово кошка было, а аттрибута нет, то вызовет метод generateSparqlQuery(String animal (#Cat))
     *
     * @param userInput ввод пользователя (запрос)
     * @return возвращает ответ бота
     */
    private String getResponse(String userInput) {
        String lowerInput = userInput.toLowerCase();
        String animal = null;
        String attribute = null;

        for (Pair key : animalQueries.keySet()) {
            if (lowerInput.contains(key.getFirst())) {
                animal = animalQueries.get(key);
                break;
            }
        }

        for (String[] key : attributeQueries.keySet()) {
            boolean found = false;
            for (String str : key) {
                if (lowerInput.contains(str)) {
                    attribute = attributeQueries.get(key);
                    found = true;
                }
            }
            if (found) {
                break;
            }
        }

        if (animal != null && attribute != null) {
            return ontologyHandler.queryOntology(generateSparqlQuery(animal, attribute), userInput, animalQueries, attributeQueries);
        } else if (animal != null) {
            return ontologyHandler.queryOntology(generateSparqlQuery(animal), userInput, animalQueries, attributeQueries);
        } else if (lowerInput.contains("животные")) {
            return ontologyHandler.queryOntology(generateSparqlQueryForAllAnimals(), userInput, animalQueries, attributeQueries);
        } else {
            return "Нет данных по запросу.";
        }
    }

    /**
     * Метод генерирует SpaRQL запрос, который будем в дальнейшем передавать в метод queryOntology() класса OntologyHandler.
     *
     * @param animal    параметр animal отвечает за животного, про которого спрашивает пользователь. Далее исходя из этого животного, формируется его URI.
     * @param attribute параметр attribute отвечает за аттрибут, про который спрашивает пользователь. Далее исходя из этого аттрибута, формируется его URI.
     * @return метод возвращает SpaRQL запрос, который передадим в метод queryOntology()
     */
    private String generateSparqlQuery(String animal, String attribute) {
        String animalUri = ONTOLOGY_LINK + animal + ">";
        String attributeUri = ONTOLOGY_LINK + attribute + ">";
        if (attributeQueries.containsValue(attribute)) {
            return "SELECT ?name ?"
                    + attribute.substring(3).toLowerCase()
                    + " WHERE { " + animalUri + " " + ONTOLOGY_LINK
                    + "hasName> ?name . " + animalUri + " " + attributeUri
                    + " ?" + attribute.substring(3).toLowerCase() + " . }";
        } else {
            return "SELECT ?name WHERE { " + animalUri + " " + attributeUri + " ?name . }";
        }
    }

    /**
     * Метод генерирует SpaRQL запрос, который будем в дальнейшем передавать в метод queryOntology() класса OntologyHandler.
     *
     * @param animal параметр animal отвечает за животного, про которого спрашивает пользователь. Далее исходя из этого животного, формируется его URI.
     * @return метод возвращает SpaRQL запрос, который передадим в метод queryOntology()
     */
    private String generateSparqlQuery(String animal) {
        String animalUri = ONTOLOGY_LINK + animal + ">";
        return "SELECT ?predicate ?object WHERE { " + animalUri + " ?predicate ?object . }";
    }

    /**
     * Метод генерирует SpaRQL запрос, для вывода информации о всех животных, которые есть в онтологии.
     *
     * @return метод возвращает SpaRQL запрос, который передадим в метод queryOntology()
     */
    private String generateSparqlQueryForAllAnimals() {
        return "SELECT ?subject ?predicate ?object WHERE { ?subject a " + ONTOLOGY_LINK + "Animal> . ?subject ?predicate ?object . }";
    }
}
