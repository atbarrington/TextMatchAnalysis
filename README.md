TextMatchAnalysis
=================

Simple text analysis tool to count the frequency and percentage of words listed on a website that are contained within a source of desired words file. Words are checked as valid dictionary words to contribute to the total word count. Both a valid word dictionary file and desired counting word file must be supplied. Samples are provided. 

The program is run with 3 input parameters as follows:

1. File - input file containing newline-delimited web addresses to be analyzed for word count
2. File - desired output file (will be removed in future versions to allow flexibility with file redirection)
3. Int  - number of threads to concurrently analyze website data

The software contains 2 important folders that are important to mention:

* res - the resouce file that **must** contain a `dictionary.txt` and `words.txt file`. `dictionary.txt` contains all valid words to be recognized on a website delimited by newlines. `words.txt` contains the words to be counted for frequency.
* src - the src file is optional in this release, however in future releases it will contain *all* files containing web addresses to be analyzed. It is recommended to organize lists of web address files in this directory.
