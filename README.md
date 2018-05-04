# Enriching Lexicons with Ephemeral Words for Sentiment Analysis in Social Streams
# Ephemeral entities extraction

With the rise of Web 2.0,  more and more words that are not sentimental are often
associated with positive/negative feelings. In the following Apache Spark project we present an approach 
for extracting what we call ephemeral entities from social streams.

The project generates: 
- **sentiment analysis** (using lexicon-based approach SentiWordNet 3.0 )
- **not lexicon words extraction (set Extraction)** (generating a dictionary for each month)

The dataset should follow the following format using as a separator ",":
- SENTIMENT_OF_TEXT (positive=4/negative=0/neutral=2)
- TWEET-ID(long)
- POST_DATE
- TWEET-QUERY
- USER 
- TWEET (string)

Example:
> "0","1550724000","Sat Apr 18 07:04:07 PDT 2009","NO_QUERY","trwiles","Wishing I was going to play golf today. Yard work instead. YAY "

The project was built using Scala 2.10.6 and Spark version 1.6.0 for 
supporting the distributed computation. 

The main (MainSentiment.scala) file expects two arguments, the first one is the path to the dataset and the second one is the output directory file.

- **MainSentiment.scala** : Object that is in charge of calling to the different methods for applying the preprocessing, the sentiment analysis tasks and extracting the words considered as ephemeral.
. 
It writes the following files as outputs :
    * *sentimentDataset.csv*: file that contains the dataset after applying the preprocessing and sentiment analysis tasks. The output format
uses the character separator ",".	      
      * no sentiment words + the sentiwordnet sentiment (case tweets): groups the words that are not part of the lexicon along with the sentiment from sentiwordnet case tweets (WordsNoSentimentLabelsSentiWordNetTweets.csv).
      * no sentiment words + the dataset sentiment (case tweets): groups the words that are not part of the lexicon along with the sentiment from dataset case tweets (WordsNoSentimentLabelsDistantSupervisionTweets.csv).
      * no sentiment words + the sentiwordnet sentiment (case tokens): groups the words that are not part of the lexicon along with the sentiment from sentiwordnet case tokens (WordsNoSentimentLabelsSentiWordNetTokens.csv).
      * no sentiment words + the dataset sentiment (case tokens): groups the words that are not part of the lexicon along with the sentiment from dataset case tokens (WordsNoSentimentLabelsDistSupervisionTokens.csv).            
      * hashtags words (contains the hashtags found during the preprocessing and sentiment analysis tasks)        
- **PrParameters.scala:** object that contains the paths of the  models, datasets, libraries and other external files necessary for the project



 
