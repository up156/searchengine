package searchengine.services;

import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Transactional
@AllArgsConstructor
public class IndexThread extends Thread {

    private final Site site;
    private final StatisticsServiceImpl statisticsService;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;


    @Override
    public void run() {

        PageFinder.setHashSet(new HashSet<>());
        PageFinder pageFinder = new PageFinder(site.getUrl(), site, siteRepository, pageRepository);
        ForkJoinPool forkJoinPool = new ForkJoinPool();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        forkJoinPool.invoke(pageFinder);

        forkJoinPool.shutdownNow();
        site.setStatusTime(ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC));
        siteRepository.save(site);
        List<Page> pageList = pageRepository.findAllBySite(site);
        pageList = pageList.stream().filter(p -> p.getCode() == 200L).toList();
        statisticsService.indexPages(pageList);
        site.setStatusTime(ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC));
        site.setStatus(Status.INDEXED);
        siteRepository.save(site);

    }
}
