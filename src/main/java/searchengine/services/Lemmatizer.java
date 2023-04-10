package searchengine.services;

import lombok.Data;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@Component
@Data
public class Lemmatizer {
    public HashMap<String, Long> lemmatizeText(String text) {


        HashMap<String, Long> result = new HashMap<>();
        List<String> forms = new ArrayList<>();

        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            List<String> stringList = List.of(text.toLowerCase().replaceAll("[^а-я]", " ").trim().split("\s+"));
            System.out.println(stringList);

            stringList
                    .stream()
                    .filter(s -> s.length() < 50)
                    .filter(s -> s.length() > 1)
                    .filter(s ->
                            !luceneMorph.getMorphInfo(s).toString().contains("СОЮЗ") &&
                                    !luceneMorph.getMorphInfo(s).toString().contains("ПРЕДЛ") &&
                                    !luceneMorph.getMorphInfo(s).toString().contains("МЕЖД") &&
                                    !luceneMorph.getMorphInfo(s).toString().contains("\sЧАСТ"))
                    .forEach(s -> {
                        forms.add(luceneMorph.getNormalForms(s).get(0));
                    });

            for (String word : forms) {
                if (result.containsKey(word)) {
                    result.put(word, result.get(word) + 1L);
                } else {
                    result.put(word, 1L);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println(forms);
        System.out.println(result);
        return result;

    }
}