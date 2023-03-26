package searchengine.repository;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.Index;

import java.util.List;

public interface IndexRepository extends CrudRepository<Index, Long> {

    List<Index> findAllByPageId(Long id);
}
