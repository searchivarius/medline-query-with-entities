package edu.cmu.lti.oaqa.bio.index.medline.annotated.query;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import com.google.common.base.*;


/**
 * 
 * A simple class that transforms Pubmed-like keyword[field] constructs
 * into SOLR-annographix queries, all query parts are connected using 
 * the default operator.
 * 
 * @author Leonid Boytsov
 *
 */

public class QueryTransformer {
  
  private static final String   CONCEPT_ID_SUFFIX = "_id";
  private static final int      PADDING_SIZE = 2; // This is a bit adhoc

  enum QueryStates {
    init,      // initial/default state
    textInput  // entering a keyword
  };
  
  ArrayList<String>    mQueryParts= new ArrayList<String>();
  String               mFieldNames[] = {
      "Chemical",
      "Disease",
      "DNAMutation",
      "Gene",
      "ProteinMutation",
      "SNP",
      "Species"
  };
  
  
  CharMatcher  mSpaceMatcher        = CharMatcher.BREAKING_WHITESPACE;
  Joiner       mQueryPartJoiner;
  Joiner       mCommaJoiner         = Joiner.on(",");
  
  String          mFieldNameList = mCommaJoiner.join(mFieldNames).toLowerCase();
  HashSet<String> mhFieldNames = new HashSet<String>();
  
  boolean     mIsDebug = false;
  boolean     mIsAnd = false;
  
  SolrTokenizerWrapper  mTokenizer;

  public QueryTransformer(String query0, boolean debug) throws ParsingException {
    init(query0, "OR", debug);
  }
  
  public QueryTransformer(String query0) throws ParsingException {
    init(query0, "OR", false);
  }
  
  public QueryTransformer(String query0, String joinOp) throws ParsingException {
    init(query0, joinOp, false);
  }
  
  public QueryTransformer(String query0, String joinOp, boolean debug) throws ParsingException {
    init(query0, joinOp, debug);
  }  
  
  void init(String query0, String joinOp, boolean debug) throws ParsingException {
    mIsDebug = debug;
    mQueryPartJoiner = Joiner.on(" " + joinOp + " ");
    
    TokenizerParams       params = new TokenizerParams("solr.StandardTokenizerFactory");
    params.addArgument("maxTokenLength", "128");
    try {
      mTokenizer = new SolrTokenizerWrapper(params);
    } catch (Exception e) {
      throw new ParsingException("Cannot init SOLR tokenizer: " + e);
    }
    
    
    for (String s: mFieldNames) mhFieldNames.add(s.toLowerCase());
    
    // All whitespaces become simply spaces
    String query = mSpaceMatcher.replaceFrom(query0, ' ') +
                  " "; // a sentinel to simplify the parsing algorithm;
    
    StringBuffer  keyPhrase = null;

    char          termSymbol = ' ';
    QueryStates   state = QueryStates.init;
    
    int pos = 0;
    while (pos < query.length()) {
      char c = query.charAt(pos);
      
      if (QueryStates.init == state) {
        keyPhrase = null;
        
        if (c == ' ') {
          ++pos;
          continue;
        }
        if (c == '"') {
          state = QueryStates.textInput;
          keyPhrase = new StringBuffer();
          termSymbol = '"';
          ++pos;
          continue;
        }
        if (c == '[') {
          ++pos;
          throw 
            new ParsingException("Field-staring symbol '[' in position " + pos + " should be preceed by a key-phrase or an asterisk");
        }
        termSymbol = ' ';
        state = QueryStates.textInput;
        keyPhrase = new StringBuffer();
        continue; // don't increase pos here!        
      } else if (QueryStates.textInput == state) {
        if (c == '\\') {
          ++pos;
          if (pos >= query.length()) {
            throw new ParsingException("Expecting a symbol after the backslash in position " + pos);
          }
          c = query.charAt(pos);
          if (c == ' ' || c == '"' || c == '[' || c == '*' || c == '\\' || c == ']') {
            keyPhrase.append(c);
          } else {
            throw 
              new ParsingException("Expecting a space, a double quote, a backslash, an asterisk, or a square bracket after the backslash in position " + pos);
          }
          ++pos;
          continue;
        } else if (c != termSymbol && c != '[') {
          keyPhrase.append(c);
          ++pos;
        } else {
          if (c == termSymbol) ++pos;
          // found the text terminating symbol, let's see if there is a following field definition          
          while (pos < query.length() && query.charAt(pos) == ' ') {
            ++pos;
          }

          if (pos == query.length() || query.charAt(pos) != '[') {
            mQueryParts.add(makeSubQuery(keyPhrase.toString(), "", termSymbol == '"'));
            keyPhrase = null;
            state = QueryStates.init;
            continue; // don't increment pos here!
          } else {            
            ++pos;
            if (query.charAt(pos-1) != '[')
              throw new RuntimeException("Bug: expected '[' in position: " + pos);
            int endPos = query.indexOf(']', pos);
            if (endPos < 0)
              throw new ParsingException("Didn't find the field-ending symbol ']' after position: " + pos);
            String fieldName = query.substring(pos, endPos);
            
            if (fieldName.isEmpty())
              throw new ParsingException("Empty field name at position: " + pos);
              
            mQueryParts.add(makeSubQuery(keyPhrase.toString(), fieldName, termSymbol == '"'));
            keyPhrase = null;
            state = QueryStates.init;
            pos = endPos + 1;
            continue;
          }
        }
      }      
    }
  }
  
  public String getQuery()  throws Exception {
    return mQueryPartJoiner.join(mQueryParts);
  }
  
  public String makeSubQuery(String keyPhrase, String fieldName, boolean hasQuotes) throws ParsingException  {
    fieldName = fieldName.toLowerCase();
    keyPhrase = keyPhrase.toLowerCase();
    
    String qs = hasQuotes ? "\"" : "";
    
    if (mIsDebug) {
      if (fieldName.isEmpty()) fieldName = "NONE";
      if (keyPhrase.isEmpty()) keyPhrase = "*";

      return qs + keyPhrase + qs + ":" + fieldName;
    } else {
      if (fieldName.isEmpty()) {
        return qs + keyPhrase + qs;
      }
      boolean isConcept = false;
      if (fieldName.endsWith(CONCEPT_ID_SUFFIX)) {
        fieldName = fieldName.substring(0, fieldName.length() - CONCEPT_ID_SUFFIX.length());
        isConcept = true;
      }
      if (!mhFieldNames.contains(fieldName)) {
        throw new ParsingException("Invalid pseudo-field name: " + fieldName + " available list: " + mFieldNameList);
      }
      StringBuffer sb = new StringBuffer();
      
      if (isConcept) {
        if (keyPhrase.equals("*")) {
          throw new ParsingException("The concept id cannot be '*'!");
        }
        sb.append("_query_:\"{!annographix ver=3} "); // start         
        sb.append(String.format(" @0:%s_%s @1:%s_%s #covers(0,1)",
                                UtilConstMedline.CONCEPT_INDEX_PREFIX,   fieldName,
                                UtilConstMedline.CONCEPTID_INDEX_PREFIX, keyPhrase));
      } else if (keyPhrase.equals("*")) {        
        // Here fieldName can't be empty
        if (fieldName.isEmpty()) 
          throw new RuntimeException("Bug: field shouldn't be empty at this place");
        return UtilConstMedline.ANNOT_FIELD + ":" + UtilConstMedline.CONCEPT_INDEX_PREFIX + "_" + fieldName;
      } else {
        ArrayList<String> keyPhraseParts = new ArrayList<String>();
        int windowSize = keyPhrase.length();
        
        try {
          for (AnnotationProxy a: mTokenizer.tokenize(keyPhrase, 0)) {
            keyPhraseParts.add(a.mText);
            windowSize += PADDING_SIZE;
          }
        } catch (IOException e) {
          throw new ParsingException("SOLR tokenizer error: " + e);
        }
        
        sb.append(String.format("_query_:\"{!annographix ver=3 span=%d} ", windowSize)); // start
        sb.append(String.format(" @0:%s_%s",
            UtilConstMedline.CONCEPT_INDEX_PREFIX, fieldName));     
        
        for (int k = 0; k < keyPhraseParts.size(); ++k) {
          sb.append(String.format(" ~%d:%s #covers(0,%d)", 
              k+1, keyPhraseParts.get(k), 
              k+1)); 
        }
        
      }
      
      sb.append("\"");  // end
      
      return sb.toString();
    }
  }
  
  public static void main(String args[]) {
    try {
      
      if (args.length == 1) {
        String str = args[0];
        System.out.println("Query to parse:\n" + str);
        QueryTransformer qt = new QueryTransformer(str, "AND", true);
        
        System.out.println(qt.getQuery());
      }
      BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
      
      String str;
      
      while ((str= bufferRead.readLine()) != null) {
        QueryTransformer qt = new QueryTransformer(str, false);
        
        System.out.println(qt.getQuery());
      }
        
      
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println(e);
      System.exit(1);
    }

  }  
  
}