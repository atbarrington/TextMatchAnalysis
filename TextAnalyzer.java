import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class TextAnalyzer implements Runnable {

	private static int numThreads;
	private static File sourceFile;
	private static File dataFile;
	private static List<URL> urls;
	private static ConcurrentHashMap<String, Integer> dictionary;
	private static ConcurrentHashMap<String, Integer> wordBank;
	private static int totalWords = 0;
	private static Semaphore finish;
	
	public TextAnalyzer() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run() {
		StringBuilder sb = new StringBuilder();
		while (!urls.isEmpty()) {
			// fetch and open a url
			URL url = urls.remove(0);
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
			} catch (IOException e) {
				//e.printStackTrace();
				System.out.println("Failed to open connection to url: " + url.toString());
			}
			// parse the content of that url
			String line = "";
			try {
				line = reader.readLine();
				while (line != null) {
					sb.append(line);
					//System.out.println(line);
					line = reader.readLine();
				}
			} catch (IOException e) {
				//e.printStackTrace();
				System.out.println("Failed to parse url: " + url.toString() + " at line: " + line); 
			}
			String html = sb.toString();
			Document doc = Jsoup.parse(html);
			Elements bodyTexts = doc.select("p");
			for (Element text : bodyTexts) {
				for (String word : text.toString().split(" ")) {
					countWord(word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase());
				}
			}		
		}
		finish.release();	
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		// handle the args and parse the source url file
		if (!parseArguements(args))
			return;
		urls = Collections.synchronizedList(new ArrayList<URL>());
		if (!parseURLs())
			return;
		
		dictionary = new ConcurrentHashMap<String, Integer>();
		wordBank = new ConcurrentHashMap<String, Integer>();
		if (!readDictionary())
			return;
		
		// start the threads
		parseThreads(args);
		startThreads();
		// wait for threads to finish
		try {
			finish.acquire(numThreads);			
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.out.println("Internal execution error..");
		}
		
		printReport();
	}
	
	/**
	 * Parses the arguements in the array, placing the values into the sourceFile and dataFile Strings respectively.
	 * 
	 * @param args The command line arguements passed to the program
	 * @return Returns true if the source and data files were passed in args and successfully parsed; false otherwise
	 */
	private static boolean parseArguements(String[] args) {
		if (args.length < 2) {
			System.out.println("Terminating - Incorrect parameter count: " + args.length);
			usage();
			return false;
		}
		sourceFile = new File(args[0]);
		dataFile = new File(args[1]);
		return true;
	}
	
	/**
	 * Parses the urls placed of the sourceFile passed. Places each line in of the file into a url storing concurrent ArrayList
	 *  
	 * @return Returns true if the sourceFile contains only urls, one per line, and the file is successfully parsed; false otherwise
	 */
	private static boolean parseURLs() {
		// create a reader for the source file
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(sourceFile));
		} catch (FileNotFoundException e) {
			//e.printStackTrace();
			System.out.println("No source file exists: " + sourceFile.toString());
			return false;
		}
		
		// parse the source file adding each line as a url to the list
		String file = "error";
		int lineNum = 1;
		try {
			file = reader.readLine();
			while (file != null) {
				urls.add(new URL(file));
				lineNum++;
				file = reader.readLine();
			}
		} catch (IOException e) {
			System.out.println("Failed on line " + lineNum + ": " + file);
			//e.printStackTrace();
			return false;
		}
		
		// fail if no urls were in file
		if (urls.isEmpty()) {
			System.out.println("No urls provided in the source file: " + sourceFile.toString());
			return false;
		}
		
		return true;
	}
	
	private static boolean readDictionary() {
		// open the dictionary file
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(new File("res/dictionary.txt")));
		} catch (FileNotFoundException e) {
			//e.printStackTrace();
			System.out.println("Could not open source dictionary file.");
			return false;
		}
		// read the legal words from the dictionary to give valid words for TOTAL counting
		try {
			String word = reader.readLine();
			while (word != null) {
				word = word.toLowerCase().trim();
				if (!word.isEmpty())
					dictionary.put(word, 0);
				word = reader.readLine();
			}
		} catch (IOException e) {
			//e.printStackTrace();
			System.out.println("Failed while reading source dictionary file.");
			return false;
		}
		
		// open the wordbank file
		try {
			reader = new BufferedReader(new FileReader(new File("res/words.txt")));
		} catch (FileNotFoundException e) {
			//e.printStackTrace();
			System.out.println("Could not open source words file.");
			return false;
		}
		// read the legal words from the words source to give valid words for counting
		try {
			String word = reader.readLine();
			while (word != null) {
				word = word.toLowerCase().trim();
				if (!word.isEmpty())
					wordBank.put(word, 0);
				word = reader.readLine();
			}
		} catch (IOException e) {
			//e.printStackTrace();
			System.out.println("Failed while reading source words file.");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Parses any thread parameters passed setting the numThreads value to that passed or 1 otherwise.
	 * 
	 * @param args The command line arguements passed to the program
	 * @return Returns true if a third parameter was passed and it was an int value saved in the numThread value; false otherwise
	 */
	private static boolean parseThreads(String[] args) {
		numThreads = 1;
		if (args.length == 3) {
			try {
				numThreads = Integer.parseInt(args[2]);
			} catch (NumberFormatException e) {
				numThreads = 1;
				System.out.println("Failed to parse thread count: " + args[2]);
				System.out.println("Thread count set to 1");
				usage();
				return false;
			}
		} else {
			return false;
		}
		return true;
	}
	
	private static boolean startThreads() {
		finish = new Semaphore(numThreads);
		try {
			finish.acquire(numThreads);			
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
		for (int i=0; i<numThreads; i++) {
			Thread t = new Thread(new TextAnalyzer());
			t.start();
		}
		return true;
	}
	
	/**
	 * Prints program usage
	 */
	private static void usage() {
		System.out.println("\t[1]String: source file containing 1 url per line");
		System.out.println("\t[2]String: output file for calculated data");
		System.out.println("\t(3)Integer: number of threads to calculate with");
	}
	
	private static void countWord(String word) {
		if (dictionary.get(word) != null) {
			totalWords++;
		}
		if (wordBank.get(word) != null) {
			int count = wordBank.get(word);
			wordBank.put(word, count+1);
		}
	}
	
	private static void printReport() {
		DecimalFormat df = new DecimalFormat("#.######");
		List<Map.Entry<String, Integer>> mostCommon = new ArrayList<Map.Entry<String, Integer>>(); 
		for (Map.Entry<String, Integer> entry : wordBank.entrySet()) {
			mostCommon.add(entry);
			//System.out.println(entry.getKey() + " | " + entry.getValue());
		}
		
		Collections.sort(mostCommon, new Comparator<Map.Entry<String, Integer>>() {
			@Override
			public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
				
				/**
				 * Sort by word
				 */
				if (o1.getKey() != o2.getKey()) {
					// sort by count
					return o1.getKey().compareTo(o2.getKey()); 
				}
				// sort alphabetically if count is equal
				return o1.getValue().compareTo(o2.getValue());
				//*/
				
				/**
				 *  Sort by count
				 *
				if (o1.getValue() != o2.getValue()) {
					// sort by count
					return o2.getValue().compareTo(o1.getValue()); 
				}
				// sort alphabetically if count is equal
				return o1.getKey().compareTo(o2.getKey());
				*/
			}
		});
		
		FileWriter outfile = null;
		try {
			outfile = new FileWriter(dataFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		int totalPersuasive = 0;
		for (int i=0; i<mostCommon.size(); i++) {
			totalPersuasive = totalPersuasive + mostCommon.get(i).getValue();
		}
		if (outfile != null) {
			try {
				StringBuilder sb = new StringBuilder();
				sb.append(totalWords);
				sb.append("\t");
				sb.append(df.format((float) totalPersuasive/totalWords));
				sb.append("\n\n");
				outfile.write(sb.toString());
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error writing a line to file. Data may be misrepresented. Terminating output.");
				return;
			}
		}
		for (int i=0; i<mostCommon.size(); i++) {
			Entry<String, Integer> entry = mostCommon.get(i);
			int entryCount = entry.getValue();
			//if (entryCount < 1)
				//break;
			
			String dataLine = entry.toString();
			System.out.println(dataLine);
			if (outfile != null) {
				try {
					StringBuilder sb = new StringBuilder(dataLine);
					sb.append("\t");
					sb.append(df.format((float) entryCount/totalWords));
					sb.append("\n");
					outfile.write(sb.toString());
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error writing a line to file. Data may be misrepresented. Terminating output.");
					return;
				}
			}
			
		}
		try {
			outfile.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to close output file. Potential rip in space-time generated. Please try again :D");
		}
	}
}
