package searchengine.repository;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.Lemma;

import java.util.List;

public interface LemmaRepository extends CrudRepository<Lemma, Long> {

    List<Lemma> findAllByLemma(String lemma);


}
