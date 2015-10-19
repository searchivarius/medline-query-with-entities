/*
 * Copyright 2015 Carnegie Mellon University
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
package edu.cmu.lti.oaqa.bio.query;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;

class EntityEntry {
  public static ArrayList<EntityEntry> parseEntityDesc(String entityDesc) throws ParsingException {
    ArrayList<EntityEntry> res = new ArrayList<EntityEntry>();
    
    for (String line : mSplitOnNL.splitToList(entityDesc)) {
      if (line.isEmpty()) continue;
      
      EntityEntry e = new EntityEntry(line);
      res.add(e);
    }
    
    return res;
  }  
  
  private EntityEntry(String line) throws ParsingException {
    if (line.isEmpty()) throw new ParsingException("Cannot parse an empty line!");
    
    List<String> parts = mSplitOnTAB.splitToList(line);
    if (parts.size() != 4) {
      throw new ParsingException(
          "The entity line is expected to have four TAB-separated fields, but it has "   + parts.size());
    } else {   
      try {
        mStart = Integer.parseInt(parts.get(0));
        mEnd = Integer.parseInt(parts.get(1));
      } catch (NumberFormatException e) {
        throw new ParsingException("Cannot parse start or end offset (offset is not an integer)");
      }
              
      mConcept = parts.get(2);        
      mConceptIds = new ArrayList<String>();
      /*
       * The concept ID string can have multiple IDs separated by "|"
       */
      for (String conceptID: mSplitOnPipe.split(parts.get(3))) 
      if (!conceptID.isEmpty()) {            
        mConceptIds.add(conceptID);
      }
    }        
  }
  
  public final int       mStart, mEnd;
  public final String    mConcept;
  
  public final ArrayList<String>  mConceptIds;
  
  private static Splitter    mSplitOnTAB  = Splitter.on('\t');
  private static Splitter    mSplitOnPipe = Splitter.on(Pattern.compile("[,|]"));
  private static Splitter    mSplitOnNL   = Splitter.on('\n');
}