package searchengine.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface PageRepository extends CrudRepository<Page, Long> {

    List<Page> findAllByPath(String path);

    List<Page> findAllBySite(Site site);

    List<Page> findAllByPathAndSite(String path, Site site);

}
