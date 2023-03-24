package searchengine.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;
import searchengine.model.Status;

import java.util.List;

@Repository
public interface SiteRepository extends CrudRepository<Site, Long> {

    Site findByUrl(String url);
    List<Site> findAllByStatus(Status status);
}
