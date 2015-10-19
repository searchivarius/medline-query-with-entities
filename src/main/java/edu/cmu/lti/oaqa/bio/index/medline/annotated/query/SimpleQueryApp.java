/*
 *  Copyright 2015 Carnegie Mellon University
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.cmu.lti.oaqa.bio.index.medline.annotated.query;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

import org.apache.commons.cli.*;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import com.google.common.base.Joiner;


/**
 * 
 * A simple query application that transforms Pubmed-like keyword[field] constructs
 * into SOLR-annographix queries, all query parts are connected using 
 * the default operator.
 * 
 * @author Leonid Boytsov
 *
 */
public class SimpleQueryApp { 
  static void Usage(String err) {
    System.err.println("Error: " + err);
    System.err.println("Usage: " 
                       + "-u <Target Server URI> "
                       + "-n <Max # of results> "
                       );
    System.exit(1);
  }  

  public static void main(String[] args) {
    Options options = new Options();
    
    options.addOption("u", null, true, "Solr URI");
    options.addOption("n", null, true, "Max # of results");

    CommandLineParser parser = new org.apache.commons.cli.GnuParser(); 
    
    try {
      CommandLine cmd = parser.parse(options, args);
      String solrURI = null;

      solrURI = cmd.getOptionValue("u");
      if (solrURI == null) {
        Usage("Specify Solr URI");
      }
      
      SolrServerWrapper solr = new SolrServerWrapper(solrURI);
      
      int numRet = 10;
      
      if (cmd.hasOption("n")) {
        numRet = Integer.parseInt(cmd.getOptionValue("n"));
      }
      
      List<String> fieldList = new ArrayList<String>();
      fieldList.add(UtilConstMedline.ID_FIELD);
      fieldList.add(UtilConstMedline.SCORE_FIELD);
      fieldList.add(UtilConstMedline.ARTICLE_TITLE_FIELD);
      fieldList.add(UtilConstMedline.ENTITIES_DESC_FIELD);
      fieldList.add(UtilConstMedline.ABSTRACT_TEXT_FIELD);

      BufferedReader sysInReader = new BufferedReader(new InputStreamReader(System.in));
      Joiner         commaJoiner = Joiner.on(',');
      
      while (true) {
        System.out.println("Input query: ");
        String query = sysInReader.readLine();
        if (null == query) break;

        QueryTransformer qt = new QueryTransformer(query);
        
        String tranQuery = qt.getQuery();
            
                
        System.out.println("Translated query:");
        System.out.println(tranQuery);
        System.out.println("=========================");
        
        SolrDocumentList res = solr.runQuery(tranQuery, fieldList, numRet);
        
        System.out.println("Found " + res.getNumFound() + " entries");
  
        for (SolrDocument doc : res) {
          String id  = (String)doc.getFieldValue(UtilConstMedline.ID_FIELD);
          float  score = (Float)doc.getFieldValue(UtilConstMedline.SCORE_FIELD);
          String title = (String)doc.getFieldValue(UtilConstMedline.ARTICLE_TITLE_FIELD);
          String titleAbstract = (String)doc.getFieldValue(UtilConstMedline.ABSTRACT_TEXT_FIELD);
          
          System.out.println(score + " PMID=" + id + " " + titleAbstract);
          
          String entityDesc = (String)doc.getFieldValue(UtilConstMedline.ENTITIES_DESC_FIELD);
          System.out.println("Entities:");
          for (EntityEntry e: EntityEntry.parseEntityDesc(entityDesc)) {
            System.out.println(String.format("[%d %d] concept=%s concept_ids=%s",
                                              e.mStart, e.mEnd, e.mConcept, 
                                              commaJoiner.join(e.mConceptIds)));
          }
        }      
      }
      
      solr.close();

    } catch (ParseException e) {
      Usage("Cannot parse arguments");
    } catch(Exception e) {
      System.err.println("Terminating due to an exception: " + e);
      System.exit(1);
    } 
  }
}
