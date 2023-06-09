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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.RecursiveTask;

@Log4j2
@Transactional
public class PageFinder extends RecursiveTask<HashSet<Page>> {

    private final String pageUrl;
    private final Site initial;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private static HashSet<String> hashSet = new HashSet<>();

    public static void setHashSet(HashSet<String> hashSet) {
        PageFinder.hashSet = hashSet;
    }

    public PageFinder(String pageUrl, Site initial, SiteRepository siteRepository, PageRepository pageRepository) {
        this.initial = initial;
        this.pageUrl = pageUrl;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;

    }

    @Override
    protected HashSet<Page> compute() {

        HashSet<String> pageList = new HashSet<>();
        HashSet<Page> pageResult = new HashSet<>();

        if (siteRepository.findById(initial.getId()).get().getStatus().equals(Status.FAILED)) {

            this.cancel(true);

        } else {
            try {
                try {

                    Thread.sleep(2500);
                    Connection connection = Jsoup.connect(pageUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36")
                            .referrer("http://www.google.com")
                            .followRedirects(false)
                            .timeout(20000).get().connection();
                    Long statusCode = (long) connection.response().statusCode();
                    Document doc = connection.get();
                    Page page = new Page(initial, pageUrl.substring(initial.getUrl().length() - 1), statusCode, doc.wholeText(), doc.title());
                    pageRepository.save(page);
                    Elements elements = doc.select("a");
                    HashSet<String> pages = new HashSet<>();

                    for (Element el : elements) {
                        String currentPage = el.attr("abs:href");

                        if (hashSet.contains(currentPage)) {
                            continue;
                        }

                        if (hashSet.size() % 100 == 0) {
                            initial.setStatusTime(ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC));
                            siteRepository.save(initial);
                        }

                        log.info("PageFinder working with page: {}", currentPage);
                        hashSet.add(currentPage);

                        String suffix = (pageUrl.substring(pageUrl.indexOf(".")));
                        if (suffix.contains("/")) {
                            suffix = suffix.substring(0, suffix.indexOf("/") + 1);
                        }

                        String substring = pageUrl.substring(0, pageUrl.indexOf(suffix) + suffix.length());
                        if ((currentPage.matches(substring + "[^.\\s#]+")
                                || (currentPage.matches(substring + "[^\\s#]+") && currentPage.endsWith(".html")))
                                && pageRepository.findAllByPath(currentPage.substring(initial.getUrl().length())).isEmpty()
                                && currentPage.length() < 100) {

                            pages.add(currentPage);
                        }
                    }

                    pageList.addAll(pages);

                } catch (HttpStatusException exception) {
                    exception.printStackTrace();
                    pageRepository.save(new Page(initial, exception.getUrl(), (long) exception.getStatusCode(), "NA", "NA"));

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(pageUrl);
            }

            List<PageFinder> taskList = new ArrayList<>();

            if (!siteRepository.findById(initial.getId()).get().getStatus().equals(Status.FAILED)) {
                for (String page : pageList) {

                    PageFinder task = new PageFinder(page, initial, siteRepository, pageRepository);
                    task.fork();
                    taskList.add(task);
                }

                for (PageFinder task : taskList) {

                    pageResult.addAll(task.join());
                }

            } else {

                this.cancel(true);
            }

            return pageResult;
        }

        return null;
    }
}






