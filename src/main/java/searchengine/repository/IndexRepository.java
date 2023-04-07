package searchengine.repository;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

public interface IndexRepository extends CrudRepository<Index, Long> {

    List<Index> findAllByPageId(Long id);
    List<Index> findAllByLemma(Lemma lemma);
    List<Index> findAllByLemmaAndPage(Lemma lemma, Page page);
    Index findByLemmaAndPage(Lemma lemma, Page page);
}
