<h1 align="center">searchengine</h1>

<p>The Java search engine is designed for multithreaded indexing of a given group of sites with subsequent search by their content (Russian words).</p>
<p>The optimal speed of the program is ensured by:</p>
<li>Performing indexing process of each site/page in a separate thread</li> 
<li>Using of ForkJoinPool for recursive crawling of the site and lemmatization of its pages.</li>
<br>
<p>Search engine developed on stack of technology:<p>

<li>Syntax - Java 17</li>
<li>Framework - Spring</li>
<li>Database - MySQL 8.0.33</li>
<li>Library - Russianmorphology 1.5</li>
<li>Library - JSOUP 1.15.4</li>
<li>Library - Lombok 1.18.26</li>
<li>FrontEnd - HTML, CSS, JavaScript</li>

<h2 align="left">Prepare and start project on your device</h2>
<ol>
<li>
<p>Install MySQL 8.0.26 or later.</p>
</li>
<li>Clone repository.</li>
<li>Configure application.yml:
<p>Input username and password for connect to database.</p>
<p>Input site urls and names.</p>
</li>
<li>Configure your IDE:
<p>Start Main method after maven download all project depencies.</p>
</li>
</ol>

<h2 align="left">Indexing and search</h2>
<ol>
<li>Open the searchengine start page in your browser - <a href=http://localhost:8080>http://localhost:8080</a>
</li>
<li>Click on the <b>MANAGEMENT</b> tab and click on <b>START INDEXING</b> button;
<p align="center">
<img src="https://media.giphy.com/media/BQ1PKKds5zxrc7Gle4/giphy.gif"></p>
<p><b>Please note:</b><br>
All previous data will be lost.
</p>
</li>
<li>You can check for indexing progress at the <b>DASHBOARD</b> tab. Use F5 to update the page;
</li>
<li>Once sites are indexed, you can search for a query at the <b>SEARCH</b> tab.
</li>
<p>If there are more than 10 results for your query, click on the <b>SHOW MORE</b> button to get next 10</p>
<p align="center">
<img src="https://media.giphy.com/media/RPmxRDaOIKcJvyJ4TR/giphy.gif"></p>
</ol>

<h2 align="left">Indexing a specific page</h2>
<ol>
<li>On the <b>MANAGEMENT</b> tab, input the page url and click the <b>ADD/UPDATE</b> button;
<p><b>NOTE</b><br>
Page must be subpage of one of the target sites.
</p>
</li>
<li>Check the result on the <b>SEARCH</b> tab.
<p align="center">
<img src="https://media.giphy.com/media/o0xCsP3HgxTZJUBAg6/giphy.gif"></p>
</li>
</ol>
<h2 align="left">HAVE FUN!</h2>

