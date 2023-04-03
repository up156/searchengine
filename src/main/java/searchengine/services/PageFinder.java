package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
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
public class PageFinder extends RecursiveTask<HashSet<String>> {

    private final String pageUrl;
    private final Site initial;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final StatisticsServiceImpl statisticsService;

    private static HashSet<String> hashSet = new HashSet<>();

    public static void setHashSet(HashSet<String> hashSet) {
        PageFinder.hashSet = hashSet;
    }

    public PageFinder(String pageUrl, Site initial, SiteRepository siteRepository, PageRepository pageRepository, StatisticsServiceImpl statisticsService) {
        this.initial = initial;
        this.pageUrl = pageUrl;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.statisticsService = statisticsService;

    }

    @Override
    protected HashSet<String> compute() {

        HashSet<String> pageList = new HashSet<>();
        HashSet<Page> pageListExceptions = new HashSet<>();

        if (siteRepository.findById(initial.getId()).get().getStatus().equals(Status.FAILED)) {

            System.out.println("FIRST BREAK");

        } else {

            try {
                try {

                    Thread.sleep(4500);

                    Connection connection = SSLHelper.getConnection(pageUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36")
                        .referrer("http://www.google.com")
//                            .followRedirects(false)
                            .timeout(20000).get().connection();
                    Long statusCode = (long) connection.response().statusCode();
                    Document doc = connection.get();
                    Page page = new Page(initial, pageUrl.substring(initial.getUrl().length()), statusCode, doc.body().wholeText());
                    pageRepository.save(page);
                    statisticsService.indexNewPage(page);

                    Elements elements = doc.select("a");

                    for (Element el : elements) {
                        String currentPage = el.attr("abs:href");

                        if (hashSet.contains(currentPage)) {
                            continue;
                        }

                        System.out.println(currentPage);
                        hashSet.add(currentPage);

                        String suffix = (pageUrl.substring(pageUrl.indexOf(".")));
                        if (suffix.contains("/")) {
                            suffix = suffix.substring(0, suffix.indexOf("/"));
                        }

                        if ((currentPage.matches(pageUrl.substring(0, pageUrl.indexOf(suffix) + suffix.length()) + "[^.\\s#]+")
                                || (currentPage.matches(pageUrl.substring(0, pageUrl.indexOf(suffix) + suffix.length()) + "[^\\s#]+")
                                && currentPage.endsWith(".html")))
                                && pageRepository.findAllByPath(currentPage.substring(initial.getUrl().length())).isEmpty()
                                && currentPage.length() < 100) {

                            pageList.add(currentPage);

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

            if (!siteRepository.findById(initial.getId()).get().getStatus().equals(Status.FAILED)) {
                for (String page : pageList) {

                    PageFinder task = new PageFinder(page, initial, siteRepository, pageRepository, statisticsService);
                    task.fork();
                    taskList.add(task);
                }

                for (PageFinder task : taskList) {

                        pageList.addAll(task.join());
//                    if (!siteRepository.findById(initial.getId()).get().getStatus().equals(Status.FAILED)) {
//                        initial.setStatusTime(ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC));
//                        siteRepository.save(initial);
//                    }
                    pageRepository.saveAll(pageListExceptions);
                }

            } else {
                System.out.println("FIFTH BREAK");
                this.cancel(true);
            }

            return pageList;
        }

        return null;
    }
}






