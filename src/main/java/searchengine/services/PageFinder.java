package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.RecursiveTask;

@Log4j2
@Transactional
public class PageFinder extends RecursiveTask<HashSet<Page>> {

    private final String pageUrl;
    private final Site initial;
    private SiteRepository siteRepository;
    private PageRepository pageRepository;

    private static HashSet<String> hashSet = new HashSet<>();

    private StatisticsServiceImpl statisticsService;

    public PageFinder(String pageUrl, Site initial, SiteRepository siteRepository, PageRepository pageRepository, StatisticsServiceImpl statisticsService) {
        this.initial = initial;
        this.pageUrl = pageUrl;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.statisticsService = statisticsService;

    }

    @Override
    protected HashSet<Page> compute() {

        HashSet<Page> pageList = new HashSet<>();
        HashSet<Page> pageListExceptions = new HashSet<>();
        try {
            try {
                Thread.sleep(3000);
                Connection connection = Jsoup.connect(pageUrl)
                        .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
//                        .referrer("http://www.google.com")
                        .timeout(20000).get().connection();
                Long statusCode = (long) connection.response().statusCode();
                Document doc = connection.get();
                Elements elements = doc.select("a");
                for (Element el : elements) {
                    String currentPage = el.attr("abs:href");


                    if (hashSet.contains(currentPage)) {
                        continue;
                    }
                    System.out.println(currentPage);
                    hashSet.add(currentPage);

                    if (siteRepository.findById(initial.getId()).get().getStatus().equals(Status.FAILED)) {

                        System.out.println("SECOND BREAK");
                        this.cancel(true);
                        break;
                    }
                    if (currentPage.matches(pageUrl + "[^.\\s#]+") && currentPage.length() < 100) {
                        String currentPath = currentPage.substring(initial.getUrl().length());

                        if (pageRepository.findAllByPath(currentPath).isEmpty() && !Thread.currentThread().isInterrupted()) {
                            String text = Jsoup.connect(currentPage).followRedirects(false).timeout(20000).get().body().wholeText();
                            pageList.add(new Page(initial, currentPage.substring(initial.getUrl().length()), statusCode, text));
                            statisticsService.indexPage(currentPage);
                            log.info("parsing page " + currentPage);
                            log.info("parsing page " + currentPage.substring(initial.getUrl().length()));
                        }
                    }
                }

            } catch (HttpStatusException exception) {
                exception.printStackTrace();
                pageListExceptions.add(new Page(initial, exception.getUrl(), (long) exception.getStatusCode(), "NA"));

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(pageUrl);
            pageListExceptions.add(new Page(initial, pageUrl, 500L, "NA"));
        }

        List<PageFinder> taskList = new ArrayList<>();

        for (Page page : pageList) {
            if (!siteRepository.findById(initial.getId()).get().getStatus().equals(Status.FAILED)) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("THIRD BREAK");
                    Thread.currentThread().interrupt();
                    this.cancel(true);

                    break;
                }
            }
            PageFinder task = new PageFinder(initial.getUrl() + page.getPath(), initial, siteRepository, pageRepository, statisticsService);
            task.fork();
            taskList.add(task);
        }


        for (PageFinder task : taskList) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            pageList.addAll(task.join());

        }

        pageList.addAll(pageListExceptions);
        pageRepository.saveAll(pageList);
        return pageList;
    }

}




