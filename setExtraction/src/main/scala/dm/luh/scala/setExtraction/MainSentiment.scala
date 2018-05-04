package dm.luh.scala.setExtraction

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark
import org.apache.spark.rdd.RDD
import org.apache.log4j.Logger
import org.apache.log4j.Level

/*Main object which calls all the methods to generate the dictionaries that are not part from the lexicon*/
object MainSentiment {
  
  def main(args:Array[String]): Unit  = 
  {
    val sc = createSparkEnvironment("SAnalysisTwitter")    
    //Gathering the arguments 
    PrParameters.setProjectDataset(args(0))
    PrParameters.setProjectFolder(args(1))
    
    println("Generating labels using SentiWordNEt")
    //expected arg(1) -> ResMonthsFinal/2013-01/    
    var lblTimeStamp = getLabelFromPath(args(1))
    val datasetWithoutPreproc = sc.textFile(PrParameters.projectDataset)    
    val preproDataset = preprocessingDataset(datasetWithoutPreproc, sc)
    val labeledDataset = assignSentimentDataset(preproDataset, lblTimeStamp).cache()
    showingConfusionMatrix()    
    println("Generating the ephemeral entities dictionaries")   
    
    val filterLabeledDataset = removeBlankTweets(labeledDataset).cache()
    val filterLabeledDatasetShort = getLabeledDatasetShortVersion(labeledDataset).cache()        
    //Writing into a files the words that are not the lexicon
    val (wordsLexiconLabelsSentWordnetToken, wordsLexiconLabelsDistSupToken) = combineWordsNotLexiconTokens(filterLabeledDataset, sc, lblTimeStamp)    
    val (wordsLexiconLabelsSentwordnetTweet, wordsLexiconLabelsDistSupTweet) = combineWordsNotLexiconTweets(filterLabeledDataset, sc, lblTimeStamp) 
      
    //gathering the hashtags
    storeHashTags(filterLabeledDataset, lblTimeStamp)              
    labeledDataset.unpersist()
    filterLabeledDataset.unpersist()
    
  }
  
  def createSparkEnvironment(nameEnvironment:String) :SparkContext = 
  {
    val configuration = new SparkConf().setAppName(nameEnvironment).setMaster("local")    
    val sparkContextInstance = new SparkContext(configuration)    
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)
    sparkContextInstance.setLogLevel("ERROR")
    return sparkContextInstance
  }
  
  def preprocessingDataset(dataset:RDD[String], sc:SparkContext):RDD[String] = 
  {
    sc.addFile(PrParameters.tagModelFile)
    val datasetApplyingPreprocFirst = dataset.map(line => ControllerMethods.firstSetPreprocessingSteps(line))
                               .map(line => ControllerMethods.secondSetPreprocessingSteps(line))                            
    val datasetApplyingFrequencyStep = ControllerMethods.checkForFrequecies(datasetApplyingPreprocFirst,sc)
    val datasetApplyingPreprocSecond = ControllerMethods.thirdSetPreprocessingSteps(datasetApplyingFrequencyStep, sc).cache()         
    val datasetApplyingPreprocThird = datasetApplyingPreprocSecond.map(line => ControllerMethods.fourthSetPreprocessingSteps(line))
    datasetApplyingPreprocSecond.unpersist()
    return datasetApplyingPreprocThird    
  }
  
  def assignSentimentDataset(dataset:RDD[String], labelMonth:String):RDD[String] =
  {
    val labeledDataset = dataset.map( line => ControllerMethods.assignSentiment(line))
    labeledDataset.saveAsTextFile(PrParameters.projectFolder+labelMonth+"_sentimentDataset.csv")
    return labeledDataset
  }
  
  def showingConfusionMatrix()=
  {
    println("Distant supervision No SentiwordNet No "+ ControllerMethods.getActNoPredNo()) // ActualNoPredictedNo
    println("Distant supervision No SentiwordNet Yes "+ ControllerMethods.getActNoPredYes()) // ActualNoPredictedYes
    println("Distant supervision Yes SentiwordNet No "+ ControllerMethods.getActYesPredNo()) // ActualYesPredictedNo
    println("Distant supervision Yes SentiwordNet Yes " + ControllerMethods.getActualYesPredYes()) // ActualYesPredictedYes)
    println("Distant supervision No SentiwordNet Neutral " + ControllerMethods.getActualNoPredNeutral()) //  ActualNoPrectictedNeutral
    println("Distant supervision No SentiwordNet No Sentiment "+ ControllerMethods.getActualNoPredNoSent())// ActualNoPredictedNoSentiment
    println("Distant supervision Yes SentiwordNet Neutral "+ ControllerMethods.getActualYesPredNeutral()) // ActualYesPredictedNeutral 
    println("Distant supervision Yes SentiwordNet No Sentiement "+ ControllerMethods.getActualYesPredNoSent())//  ActualYesPredictedNoSentiment    
    println("Distant supervision No Others  "+ ControllerMethods.getActualNoOthers())
    println("Distant supervision Yes Others  "+ ControllerMethods.getActualYesOthers())
  }
  
  def getLabelFromPath(label:String) :String =
  {
    var labelFinal = ""
    try
    {
     labelFinal = label.split("/")(1).replaceAll("[/]", "") 
    }catch {
      case e: ArrayIndexOutOfBoundsException => e.printStackTrace()      
    }
    
    return labelFinal
    
  }
  
  def getLabeledDatasetShortVersion(labeledDataset:RDD[String]) :RDD[String] =
  {
    val rddLabeledShort = labeledDataset.map(line => {  var arrayLine = line.split("\",\"");    
                                                              var origSent = arrayLine(0).replaceAll("[\"]", "");
                                                              var implSent = arrayLine(1).replaceAll("[\"]", "");
                                                              var procTweet = arrayLine(2).replaceAll("[\"]", "");
                                                              ("\""+origSent+"\",\""+implSent+"\",\""+procTweet+"\"") } )
    return rddLabeledShort                                                                                                  
    
  }
  
  def removeBlankTweets(labeledDataset:RDD[String]) :RDD[String]= 
  {
    val labeledDatasetNoBlankTweets = labeledDataset.filter(line =>  PostFilterTweetsWords.hasInnecTweets(line)).cache()
    return labeledDatasetNoBlankTweets
  }
  
  def storeHashTags(labeledDataset:RDD[String], label:String) =
  {
    val rddHashTags = labeledDataset.map(line => (line.split("\",\"")(7))
                                    .replaceAll("[\"]", ""))
                                    .flatMap(line => line.split(" "))
                                    .cache()      
    rddHashTags.filter(line => PostFilterTweetsWords.hasInnecesaryWords(line))
               .saveAsTextFile(PrParameters.projectFolder+label+"_HashTags.csv")           
    rddHashTags.unpersist()
    
  }
  
  def storeWordsSentiment(labeledDataset:RDD[String], labelDataset:String) = 
  {
    //gathering words that are part from the lexicon 
    val rddWordsSentiment  = labeledDataset.map(line => (line.split("\",\"")(6))
                                           .replaceAll("[\"]", ""))
                                           .flatMap(line => line.split(" "))
                                           .saveAsTextFile(PrParameters.projectFolder+labelDataset+"WordsSentiment.csv")  
  }
  
  def combineWordsNotLexiconTokens(labeledDatasetNoBlank:RDD[String], sc:SparkContext, label:String)=
  {
    val wordsNoSentLabelsSentiwordNet = labeledDatasetNoBlank.map(line => (line.split("\",\"")(3))
                                             .replaceAll("[\"]", ""))
                                             .flatMap(line => line.split(" "))
                                             .cache()    
    val wordsNoSentLabelsDistantSup = labeledDatasetNoBlank.map(line => (line.split("\",\"")(4))
                                             .replaceAll("[\"]", ""))
                                             .flatMap(line => line.split(" "))
                                             .cache()  
    //Tokens
    val filterWordsNoSentLabelsSentiwordNet = wordsNoSentLabelsSentiwordNet.filter(line => PostFilterTweetsWords.hasInnecesaryWords(line))        
    val filterWordsNoSentLabelsDistSup = wordsNoSentLabelsDistantSup.filter(line => PostFilterTweetsWords.hasInnecesaryWords(line))    
    val groupWordsNoSentLabelsSentwordnet = PostCombWordsNoSent.groupWordsNoSentiment(filterWordsNoSentLabelsSentiwordNet,sc)
    groupWordsNoSentLabelsSentwordnet.saveAsTextFile(PrParameters.projectFolder+label+"_WordsNoSentimentLabelsSentiWordNetTokens.csv")
    val groupWordsNoSentLabelsDistSup = PostCombWordsNoSent.groupWordsNoSentiment(filterWordsNoSentLabelsDistSup,sc)
    groupWordsNoSentLabelsDistSup.saveAsTextFile(PrParameters.projectFolder+label+"_WordsNoSentimentLabelsDistSupervisionTokens.csv")
    wordsNoSentLabelsSentiwordNet.unpersist()
    wordsNoSentLabelsDistantSup.unpersist()
    (groupWordsNoSentLabelsSentwordnet, groupWordsNoSentLabelsDistSup )
    
  }
  
  def combineWordsNotLexiconTweets(labeledDatasetNoBlank:RDD[String], sc:SparkContext, label:String)=
  {
    //gathering words not part on the lexicon along with the sentiment that comes from dataset case  distinct
    val wordsNoSentLabelsSentiwordNetDist = labeledDatasetNoBlank.map(line => (line.split("\",\"")(6))
                                                 .replaceAll("[\"]", ""))
                                                 .flatMap(line => line.split(" "))
                                                 .cache()
    //gathering words not part on the lexicon along with the sentiment that comes from sentiwordnet case distinct
    val wordsNoSentLabelsDistantSupDist = labeledDatasetNoBlank.map(line => (line.split("\",\"")(5))
                                                 .replaceAll("[\"]", ""))
                                                 .flatMap(line => line.split(" "))
                                                 .cache()    
    //Tweets
    val filterWordsNoSentLabelsSentiwordNetDist = wordsNoSentLabelsDistantSupDist.filter(line => PostFilterTweetsWords.hasInnecesaryWords(line))        
    val filterWordsNoSentLabelsDistSupDist = wordsNoSentLabelsSentiwordNetDist.filter(line => PostFilterTweetsWords.hasInnecesaryWords(line))
    val groupWordsNoSentLabelsSentwordnetTweet = PostCombWordsNoSent.groupWordsNoSentimentNoPOS(filterWordsNoSentLabelsSentiwordNetDist, sc).cache()
    groupWordsNoSentLabelsSentwordnetTweet.saveAsTextFile(PrParameters.projectFolder+label+"_WordsNoSentimentLabelsSentiWordNetTweets.csv")
    val groupWordsNoSentLabelsDistSupTweet  =  PostCombWordsNoSent.groupWordsNoSentimentNoPOS(filterWordsNoSentLabelsDistSupDist, sc).cache()
    groupWordsNoSentLabelsDistSupTweet.saveAsTextFile(PrParameters.projectFolder+label+"_WordsNoSentimentLabelsDistantSupervisionTweets.csv")
    wordsNoSentLabelsSentiwordNetDist.unpersist()
    wordsNoSentLabelsDistantSupDist.unpersist()
    (groupWordsNoSentLabelsSentwordnetTweet, groupWordsNoSentLabelsDistSupTweet)
    
  }
  
  def generateUpdatesLexiconAvg(wordsLexicon:RDD[(String, (String, String, Long))], label:String, output:String) =
  {
    val rddUpdatesAVGImp = wordsLexicon.map( line => UpdLexGenerate.MethodAvgUpdateLexicon(line._1,line._2._1, line._2._2, line._2._3 ))
                                       .saveAsTextFile(PrParameters.projectFolder+label+output)
    
  }
  
  def generateUpdatesLexiconPmi(labelDistSup:Boolean, labeledDatasetShort:RDD[String], 
      sc:SparkContext, wordsLexicon:RDD[(String, (String, String, Long))], mode:String, label:String, out:String) = 
  {
    val labelDistSup = false    
    val totalTweets = UpdLexGenerate.calculateTweetsTotal(sc, labeledDatasetShort, labelDistSup)    
    val totalTweetsPositive = UpdLexGenerate.calculateTweetsPosiClas(sc, labeledDatasetShort, labelDistSup)    
    val totalTweetsNegative = UpdLexGenerate.calculateTweetsNegClas(sc, labeledDatasetShort, labelDistSup)
    
    val rddMinMaxPMImp = wordsLexicon.map(line => UpdLexGenerate.CalculateMaxMin(line._1,line._2._1, line._2._2, line._2._3,totalTweets,totalTweetsPositive, totalTweetsNegative ) )
                                                    .cache()
    val valMin = rddMinMaxPMImp.min()
    val valMax = rddMinMaxPMImp.max()
    rddMinMaxPMImp.unpersist()
    println("Min "+valMin)
    println("Max "+valMax)
    println("TotalTweets: "+totalTweets)
    println("Total Tweets Positive: "+totalTweetsPositive )
    println("Total Tweets Negative: "+totalTweetsNegative)
    //Saving as a file
    val rddUpdatesPMIImp = wordsLexicon.map(line => UpdLexGenerate.MethodPmiUpdateLexicon(line._1,line._2._1, line._2._2, line._2._3, labelDistSup,totalTweets,totalTweetsPositive,totalTweetsNegative, valMax, valMin, mode ) )
                                                       .saveAsTextFile(PrParameters.projectFolder+label+out)
    
  }
  
}