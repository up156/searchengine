package searchengine.repository;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;

public interface LemmaRepository extends CrudRepository<Lemma, Long> {

    List<Lemma> findAllByLemma(String lemma);
    Lemma findByLemmaAndSite(String lemma, Site site);
    List<Lemma> findAllBySite(Site site);
}
