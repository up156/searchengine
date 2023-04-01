package searchengine.services;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.ForkJoinPool;

@Transactional
@Component
@RequiredArgsConstructor
@AllArgsConstructor
public class IndexThread extends Thread {

    private Site site;
    private StatisticsServiceImpl statisticsService;
    private PageRepository pageRepository;
    private SiteRepository siteRepository;


    @Override
    public void run() {

        PageFinder pageFinder = new PageFinder(site.getUrl(), site, siteRepository, pageRepository, statisticsService);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPool.invoke(pageFinder);
        forkJoinPool.shutdown();
        site.setStatusTime(ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC));
        site.setStatus(Status.INDEXED);
        siteRepository.save(site);

    }
}
