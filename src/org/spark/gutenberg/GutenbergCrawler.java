package org.spark.gutenberg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.spark.util.SparkUtils;

/**
 * @author Haoyu
 * 
 * You should first use 'wget -w 2 -m http://www.gutenberg.org/robot/harvest?filetypes[]=txt&langs[]=en' to get all HTML pages from gutenberg. <br/>
 * Then change the robot file dir variable to your robot file directory, zip file directory to your zip file directory and run crawlRobotFiles. It will download all zip files found in the HTML pages<br/>
 * Then change the txt file directory to the directory you like to have all unzipped files and run unzipFiles. It will unzip all zipped files into that directory <br/> 
 * 
 *
 */
public class GutenbergCrawler {
	static final String GUTENBERG_TXT_DIR = "gutenberg/txt/";

	static final String GUTENBERG_ZIP_DIR = "gutenberg/zip/";
	
	static final String GUTENBERG_FAILED_ZIP_DIR = "gutenberg/2zip/";

	static final String GUTENBERG_ROBOT_FILE_DIR = "/Users/apple/graduate/Courses/544NLP/data/gutenberg/www.gutenberg.org/robot";

	static final int GUTENBERG_ROBOT_DISPATCH_THRESHOLD = 500;

	final ExecutorService executorService = Executors.newCachedThreadPool();

	public void unzipFiles() {
		GutenbergCrawlerHelper.unzipInit();
		File zipFileDir = new File(GUTENBERG_ZIP_DIR);
		File[] zips = zipFileDir.listFiles();
		List<File> files = new ArrayList<File>();
		
		for (int i = 0; i < zips.length; i++) {
			
			if (!SparkUtils.isZipFile(zips[i].getName())) {
				SparkUtils.getLogger().info("not a zip file:" + zips[i].getName());
				continue;
			}
			
			int index = zips[i].getName().lastIndexOf(".");
			String name = zips[i].getName();
			if (index != -1) {
				name = zips[i].getName().substring(0, index);
			}
			synchronized (GutenbergCrawlerHelper.unzippedFiles) {
				if (GutenbergCrawlerHelper.unzippedFiles.contains(name)) {
					continue;
				}
			}
			files.add(zips[i]);
			if ((files.size() + 1) % GUTENBERG_ROBOT_DISPATCH_THRESHOLD == 0) {
				// dispatch file tasks
				dispatchUnzipTask(files);
				synchronized (files) {
					files.clear();	
				}
			}
		}
		if (files.size() != 0) {
			dispatchUnzipTask(files);
		}
	}

	public void downloadFailedFiles() throws IOException {
		try {
			GutenbergCrawlerHelper.init();
		} catch (IOException e) {
			e.printStackTrace();
		}
		BufferedReader br = new BufferedReader(new FileReader(new File(
				"gutenberg/failurl.txt")));
		String line = null;
		int index = 0;
		List<String> urls = Collections
				.synchronizedList(new ArrayList<String>());
		while ((line = br.readLine()) != null) {
			if ((index + 1) % GUTENBERG_ROBOT_DISPATCH_THRESHOLD == 0) {
				dispatchDownloadFailedFileTask(Collections
						.synchronizedList(new ArrayList<String>(urls)));
				urls.clear();
			}
			urls.add(line);
			index++;
		}
		if (urls.size() != 0) {
			dispatchDownloadFailedFileTask(urls);
		}
		br.close();
	}

	public void dispatchDownloadFailedFileTask(final List<String> tasks) {
		executorService.execute(new Runnable() {

			@Override
			public void run() {
				for (String task : tasks) {
					try {
						GutenbergCrawlerHelper.downloadFile(task);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
	}

	public void crawlRobotFiles() {
		try {
			GutenbergCrawlerHelper.init();
		} catch (IOException e) {
			e.printStackTrace();
		}
		File robotFileDir = new File(GUTENBERG_ROBOT_FILE_DIR);
		File[] fileNames = robotFileDir.listFiles();
		List<File> files = Collections.synchronizedList(new ArrayList<File>());

		for (int i = 0; i < fileNames.length; i++) {
			if ((i + 1) % GUTENBERG_ROBOT_DISPATCH_THRESHOLD == 0) {
				// dispatch file tasks
				synchronized (files) {
					dispatchDownloadTask(Collections
							.synchronizedList(new ArrayList<File>(files)));
				}
				files.clear();
			} else {
				files.add(fileNames[i]);
			}
		}
		if (files.size() != 0) {
			synchronized (files) {
				dispatchDownloadTask(files);
			}
		}
	}

	public void dispatchUnzipTask(final List<File> tasks) {
		executorService.execute(new Runnable() {

			@Override
			public void run() {
				try {
					synchronized (tasks) {
						for (File task : tasks) {
							GutenbergCrawlerHelper.unzipFiles(task);
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void dispatchDownloadTask(final List<File> tasks) {
		executorService.execute(new Runnable() {

			@Override
			public void run() {
				try {
					for (File task : tasks) {
						List<String> urls = getZipFileUrls(task);
						for (String url : urls) {
							GutenbergCrawlerHelper.downloadFile(url);
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	public List<String> getZipFileUrls(File task) throws IOException {
		List<String> urls = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(task));
		String line = null;
		while ((line = br.readLine()) != null) {
			int hrefIndex = line.indexOf("href");
			if (hrefIndex != -1) {
				StringBuilder urlBuilder = new StringBuilder();
				boolean start = false;
				for (int i = hrefIndex; i < line.length(); i++) {
					char c = line.charAt(i);
					if (start) {
						if (c == '\"') {
							if (SparkUtils.isZipFile(urlBuilder.toString())) {
								urls.add(urlBuilder.toString());
								SparkUtils.getLogger().info(
										"found url: " + urlBuilder.toString());
							}
							break;
						}
						urlBuilder.append(c);
					}
					if (c == '\"') {
						start = true;
					}
				}
			}
		}
		br.close();
		return urls;
	}
}
