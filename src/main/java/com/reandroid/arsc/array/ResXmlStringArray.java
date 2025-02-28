/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.arsc.array;

import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlIDMap;
import com.reandroid.arsc.item.IntegerArray;
import com.reandroid.arsc.item.IntegerItem;
import com.reandroid.arsc.item.ResXmlString;

import java.util.ArrayList;
import java.util.List;

public class ResXmlStringArray extends StringArray<ResXmlString> {
    public ResXmlStringArray(OffsetArray offsets, IntegerItem itemCount, IntegerItem itemStart, boolean is_utf8) {
        super(offsets, itemCount, itemStart, is_utf8);
    }
    @Override
    List<ResXmlString> listUnusedStringsToRemove(){
        List<ResXmlString> results=new ArrayList<>();
        ResXmlIDMap idMap = getResXmlIDMap();
        int lastIndex = -1;
        if(idMap!=null){
            lastIndex = idMap.countId();
        }
        for(ResXmlString item:listItems()){
            if(item == null
                    || item.hasReference()
                    || item.getIndex()<lastIndex){
                continue;
            }
            results.add(item);
        }
        return results;
    }
    private ResXmlIDMap getResXmlIDMap(){
        ResXmlDocument xmlDocument = getParentInstance(ResXmlDocument.class);
        if(xmlDocument != null){
            return xmlDocument.getResXmlIDMap();
        }
        return null;
    }
    @Override
    public ResXmlString newInstance() {
        return new ResXmlString(isUtf8());
    }
    @Override
    public ResXmlString[] newInstance(int length) {
        if(length == 0){
            return EMPTY;
        }
        return new ResXmlString[length];
    }

    private static final ResXmlString[] EMPTY = new ResXmlString[0];
}
