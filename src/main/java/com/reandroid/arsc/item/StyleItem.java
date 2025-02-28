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
package com.reandroid.arsc.item;

import com.reandroid.arsc.array.StyleArray;
import com.reandroid.arsc.base.Block;
import com.reandroid.arsc.io.BlockReader;
import com.reandroid.arsc.model.StyleSpanInfo;
import com.reandroid.arsc.model.StyledStringBuilder;
import com.reandroid.arsc.pool.StringPool;
import com.reandroid.json.JSONConvert;
import com.reandroid.json.JSONArray;
import com.reandroid.json.JSONObject;
import com.reandroid.xml.StyleDocument;
import com.reandroid.xml.StyleElement;

import java.io.IOException;
import java.util.*;

public class StyleItem extends IntegerArray implements JSONConvert<JSONObject> {
    private List<StyleSpanInfo> mSpanInfoList;
    private final Set<StyleItemReference> mReferences;
    private StyleIndexReference indexReference;
    private StringItem mStringItem;
    public StyleItem() {
        super();
        this.mReferences = new HashSet<>();
    }
    public StyleDocument build(String text){
        return new StyledStringBuilder(text, getSpanInfoElements()).build();
    }
    public void parse(StyleDocument document){
        Iterator<StyleElement> iterator = document.getElements();
        while (iterator.hasNext()){
            parse(iterator.next());
        }
    }
    public void parse(StyleElement element){
        addStylePiece(element.getStyleableTag(), element.getStart(), element.getEnd());
        Iterator<StyleElement> iterator = element.getElements();
        while (iterator.hasNext()){
            parse(iterator.next());
        }
    }
    protected void clearStyle(){
        if(getParent() == null){
            return;
        }
        setStylePieceCount(0);
        mSpanInfoList = null;
    }
    public void onRemoved(){
        unLinkIndexReference();
        setStylePieceCount(0);
        mSpanInfoList = null;
        StyleArray parentArray = getParentInstance(StyleArray.class);
        setParent(null);
        setIndex(-1);
        if(parentArray != null){
            parentArray.remove(this);
        }
    }
    public void linkIfRequiredInternal(){
        if(this.indexReference == null){
            linkIndexReference();
            linkAll();
        }
    }
    public void onDataLoaded(){
        linkIndexReference();
        linkAll();
    }
    private void setEndValue(int negOne){
        super.put(size()-1, negOne);
    }
    final Integer getEndValue(){
        return super.get(size()-1);
    }
    final Integer getStringRef(int index){
        int i=index * INTEGERS_COUNT + INDEX_STRING_REF;
        return super.get(i);
    }
    final void setStringRef(int index, int val){
        int i=index * INTEGERS_COUNT + INDEX_STRING_REF;
        super.put(i, val);
    }
    private void linkAll(){
        int count = getStylePieceCount();
        for(int i=0; i<count;i++){
            int[] spanInfo = getStylePiece(i);
            if(spanInfo == null){
                continue;
            }
            StringItem stringItem = getStringItem(spanInfo[0]);
            if(stringItem==null){
                continue;
            }
            link(stringItem, i);
        }
    }
    private void unlinkAll(){
        for(StyleItemReference itemReference:mReferences){
            StringItem stringItem = getStringItem(itemReference.get());
            if(stringItem!=null){
                stringItem.removeReference(itemReference);
            }
        }
        mReferences.clear();
    }
    private void link(StringItem stringItem, int index){
        if(stringItem==null){
            return;
        }
        unLink(stringItem, index);
        StyleItemReference itemReference = new StyleItemReference(this, index);
        mReferences.add(itemReference);
        stringItem.addReference(itemReference);
    }
    private void unLink(int index){
        Integer ref = getStringRef(index);
        if(ref==null){
            return;
        }
        unLink(getStringItem(ref), index);
    }
    private void unLink(StringItem stringItem, int index){
        if(stringItem == null){
            return;
        }
        StyleItemReference itemReference = new StyleItemReference(this, index);
        if(!mReferences.remove(itemReference)){
            return;
        }
        stringItem.removeReference(itemReference);
    }
    private void linkIndexReference(){
        StringItem stringItem = getStringItem(getIndex());
        unLinkIndexReference(mStringItem);
        if(stringItem == null){
            return;
        }
        StyleIndexReference reference = new StyleIndexReference(this);
        stringItem.addReference(reference);
        this.indexReference = reference;
        this.mStringItem = stringItem;
    }
    private void unLinkIndexReference(){
        unLinkIndexReference(mStringItem);
    }
    private void unLinkIndexReference(StringItem stringItem){
        this.mStringItem = null;
        StyleIndexReference reference = this.indexReference;
        if(reference == null){
            return;
        }
        this.indexReference = null;
        if(stringItem == null){
            return;
        }
        stringItem.removeReference(reference);
    }
    final Integer getFirstChar(int index){
        int i=index * INTEGERS_COUNT + INDEX_CHAR_FIRST;
        return super.get(i);
    }
    final Integer getLastChar(int index){
        int i=index * INTEGERS_COUNT + INDEX_CHAR_LAST;
        return super.get(i);
    }
    public void addStylePiece(String tag, int firstChar, int lastChar){
        StringPool<?> stringPool = getStringPool();
        if(stringPool==null){
            throw new IllegalArgumentException("Null string pool, must be added to parent StyleArray first");
        }
        StringItem stringItem=stringPool.getOrCreate(tag);
        addStylePiece(stringItem.getIndex(), firstChar, lastChar);
    }
    public void addStylePiece(int refString, int firstChar, int lastChar){
        int index=getStylePieceCount();
        setStylePieceCount(index+1);
        setStylePiece(index, refString, firstChar, lastChar);
    }
    final void setStylePiece(int index, int refString, int firstChar, int lastChar){
        unLink(index);
        int i=index * INTEGERS_COUNT;
        super.put(i+ INDEX_STRING_REF, refString);
        super.put(i+ INDEX_CHAR_FIRST, firstChar);
        super.put(i+ INDEX_CHAR_LAST, lastChar);
        link(getStringItem(refString), index);
    }
    final int[] getStylePiece(int index){
        if(index<0||index>= getStylePieceCount()){
            return null;
        }
        int[] result=new int[INTEGERS_COUNT];
        int i=index * INTEGERS_COUNT;
        result[INDEX_STRING_REF]=super.get(i);
        result[INDEX_CHAR_FIRST]=super.get(i+ INDEX_CHAR_FIRST);
        result[INDEX_CHAR_LAST]=super.get(i+ INDEX_CHAR_LAST);
        return result;
    }
    final void setStylePiece(int index, int[] three){
        if(three==null || three.length< INTEGERS_COUNT){
            return;
        }
        int i = index * INTEGERS_COUNT;
        super.put(i + INDEX_STRING_REF, three[INDEX_STRING_REF]);
        super.put(i + INDEX_CHAR_FIRST, three[INDEX_CHAR_FIRST]);
        super.put(i + INDEX_CHAR_LAST, three[INDEX_CHAR_LAST]);
    }
    final int getStylePieceCount(){
        int sz=size()-1;
        if(sz<0){
            sz=0;
        }
        return sz/ INTEGERS_COUNT;
    }
    final void setStylePieceCount(int count){
        if(count<0){
            count=0;
        }
        int cur = getStylePieceCount();
        if(count == cur && size() != 0){
            return;
        }
        if(count == 0){
            unlinkAll();
        }
        int max = count * INTEGERS_COUNT + 1;
        if(count == 0 || size()==0){
            super.setSize(max);
            setEndValue(END_VALUE);
            return;
        }
        List<int[]> copy=new ArrayList<>(getIntSpanInfoList());
        Integer end= getEndValue();
        if(end==null){
            end=END_VALUE;
        }
        super.setSize(max);
        max=count;
        int copyMax=copy.size();
        if(copyMax>max){
            copyMax=max;
        }
        for(int i=0;i<copyMax;i++){
            int[] val=copy.get(i);
            setStylePiece(i, val);
        }
        setEndValue(end);
    }
    private List<int[]> getIntSpanInfoList(){
        return new AbstractList<int[]>() {
            @Override
            public int[] get(int i) {
                return StyleItem.this.getStylePiece(i);
            }
            @Override
            public int size() {
                return StyleItem.this.getStylePieceCount();
            }
        };
    }
    public StyleSpanInfo[] getSpanInfoElements(){
        int count = getStylePieceCount();
        StyleSpanInfo[] results = new StyleSpanInfo[count];
        for(int i = 0; i < count; i++){
            int ref = getStringRef(i);
            results[i] = new StyleSpanInfo(
                    getStringFromPool(ref),
                    getFirstChar(i),
                    getLastChar(i));
        }
        return results;
    }
    public final List<StyleSpanInfo> getSpanInfoList(){
        if(mSpanInfoList!=null){
            return mSpanInfoList;
        }
        mSpanInfoList = new AbstractList<StyleSpanInfo>() {
            @Override
            public StyleSpanInfo get(int i) {
                int ref=getStringRef(i);
                if(ref<=0){
                    return null;
                }
                StyleSpanInfo spanInfo = new StyleSpanInfo(
                        getStringFromPool(ref),
                        getFirstChar(i),
                        getLastChar(i));
                if(!spanInfo.isValid()){
                    return null;
                }
                return spanInfo;
            }
            @Override
            public int size() {
                return getStylePieceCount();
            }
        };
        return mSpanInfoList;
    }
    private String getStringFromPool(int ref){
        StringItem stringItem = getStringItem(ref);
        if(stringItem!=null){
            return stringItem.get();
        }
        return null;
    }
    private StringItem getStringItem(int ref){
        StringPool<?> stringPool = getStringPool();
        if(stringPool!=null){
            return stringPool.get(ref);
        }
        return null;
    }
    private StringPool<?> getStringPool(){
        return getParentInstance(StringPool.class);
    }

    public String applyStyle(String text, boolean xml){
        return applyStyle(text, xml, false);
    }
    public String applyStyle(String text, boolean xml, boolean escapeXmlText){
        if(text == null){
            return null;
        }
        StyleDocument styleDocument = build(text);
        if(styleDocument == null){
            return text;
        }
        return styleDocument.getText(xml, escapeXmlText);
    }
    @Override
    public void setNull(boolean is_null){
        if(!is_null){
            return;
        }
        setStylePieceCount(0);
    }
    @Override
    public void onReadBytes(BlockReader reader) throws IOException {
        int nextPos=reader.searchNextIntPosition(4, END_VALUE);
        if(nextPos<0){
            return;
        }
        int len=nextPos-reader.getPosition()+4;
        super.setBytesLength(len, false);
        byte[] bts=getBytesInternal();
        reader.readFully(bts);
        onBytesChanged();
    }
    public void addSpanInfo(String tag, int first, int last){
        int index=getStylePieceCount();
        setStylePieceCount(index+1);
        StringPool<?> stringPool = getStringPool();
        if(stringPool==null){
            throw new IllegalArgumentException("Null string pool, must be added to parent StyleArray first");
        }
        StringItem stringItem=stringPool.getOrCreate(tag);
        setStylePiece(index, stringItem.getIndex(), first, last);
    }
    @Override
    public JSONObject toJson() {
        if(isNull()){
            return null;
        }
        JSONObject jsonObject=new JSONObject();
        JSONArray jsonArray=new JSONArray();
        int i=0;
        for(StyleSpanInfo spanInfo:getSpanInfoList()){
            if(spanInfo==null){
                continue;
            }
            JSONObject jsonObjectSpan=spanInfo.toJson();
            jsonArray.put(i, jsonObjectSpan);
            i++;
        }
        if(i==0){
            return null;
        }
        jsonObject.put(NAME_spans, jsonArray);
        return jsonObject;
    }
    @Override
    public void fromJson(JSONObject json) {
        setNull(true);
        if(json==null){
            return;
        }
        JSONArray jsonArray= json.getJSONArray(NAME_spans);
        int length = jsonArray.length();
        for(int i=0;i<length;i++){
            JSONObject jsonObject=jsonArray.getJSONObject(i);
            StyleSpanInfo spanInfo=new StyleSpanInfo(null, 0, 0);
            spanInfo.fromJson(jsonObject);
            addSpanInfo(spanInfo.getTag(), spanInfo.getFirst(), spanInfo.getLast());
        }
    }
    public void merge(StyleItem styleItem){
        if(styleItem==null||styleItem==this){
            return;
        }
        for(int[] info:styleItem.getIntSpanInfoList()){
            addStylePiece(info[0], info[1], info[2]);
        }
    }
    @Override
    public String toString(){
        return "Spans count = "+getSpanInfoList().size();
    }

    static final class StyleIndexReference implements WeakStringReference{
        private final StyleItem styleItem;
        StyleIndexReference(StyleItem styleItem){
            this.styleItem = styleItem;
        }
        @Override
        public void set(int val) {
            StyleArray styleArray = styleItem.getParentInstance(StyleArray.class);
            if(styleArray != null){
                styleArray.setItem(styleItem.getIndex(), null);
                styleArray.setItem(val, styleItem);
            }
        }

        @Override
        public int get() {
            return styleItem.getIndex();
        }
        @SuppressWarnings("unchecked")
        @Override
        public <T1 extends Block> T1 getReferredParent(Class<T1> parentClass) {
            if(parentClass.isInstance(styleItem)){
                return (T1) styleItem;
            }
            return null;
        }
    }

    private static final int INDEX_STRING_REF = 0;
    private static final int INDEX_CHAR_FIRST = 1;
    private static final int INDEX_CHAR_LAST = 2;

    private static final int INTEGERS_COUNT = 3;

    private static final int END_VALUE=0xFFFFFFFF;
    public static final String NAME_spans="spans";
}
