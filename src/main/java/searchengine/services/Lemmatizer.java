package searchengine.services;

import lombok.Data;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
@Component
@Data
public class Lemmatizer {
    public HashMap<String, Long> lemmatizeText(String text) throws IOException {


        HashMap<String, Long> result = new HashMap<>();
        LuceneMorphology luceneMorph =
                new RussianLuceneMorphology();
        List<String> stringList = List.of(text.toLowerCase().replaceAll("[^а-я]", " ").trim().split("\s+"));
        List<String> forms = new ArrayList<>();
        System.out.println(stringList);

        stringList
                .stream()
                .filter(s -> s.length() < 50)
                .filter(s -> s.length() > 1)
                .filter(s ->
                        !luceneMorph.getMorphInfo(s).toString().contains("СОЮЗ") &&
                                !luceneMorph.getMorphInfo(s).toString().contains("ПРЕДЛ") &&
                                !luceneMorph.getMorphInfo(s).toString().contains("МЕЖД") &&
                                !luceneMorph.getMorphInfo(s).toString().contains("ЧАСТ"))
                .forEach(s -> forms.addAll(luceneMorph.getNormalForms(s)));


        for (String word : forms) {
            if (result.containsKey(word)) {
                result.put(word, result.get(word) + 1L);
            } else {
                result.put(word, 1L);
            }
        }

//        HashMap<String, Long> sortedResult = result.entrySet()
//                .stream()
//                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
//                        (e1, e2) -> e1, LinkedHashMap::new));


        System.out.println(forms);
        System.out.println(result);
//        System.out.println(sortedResult);
//        return sortedResult;
        return result;
    }
}

