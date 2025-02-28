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
package com.reandroid.xml;

import com.reandroid.utils.collection.*;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public abstract class XMLNodeTree extends XMLNode implements Iterable<XMLNode>, SizedSupplier<XMLNode> {
    private ArrayList<XMLNode> mChildes;
    private int lastTrimSize;
    public XMLNodeTree(){
        super();
        this.mChildes = EMPTY;
    }

    public XMLNode getLast(){
        int size = size();
        if(size == 0){
            return null;
        }
        return mChildes.get(size - 1);
    }
    public void clear(){
        if(size() == 0){
            return;
        }
        synchronized (this){
            mChildes.clear();
            mChildes.trimToSize();
            lastTrimSize = 0;
        }
    }
    public<T1 extends XMLNode> int size(Class<T1> instance){
        return CollectionUtil.count(iterator(instance));
    }
    public <T1 extends XMLNode> Iterator<T1> iterator(Class<T1> instance) {
        return iterator(instance, null);
    }
    public <T1 extends XMLNode> Iterator<T1> iterator(Class<T1> instance, Predicate<T1> filter) {
        return new ComputeIterator<>(iterator(), new InstanceFunction<>(instance), filter);
    }
    public Iterator<XMLNode> iterator(Predicate<? super XMLNode> filter){
        return new IndexIterator<>(this, filter);
    }
    @Override
    public Iterator<XMLNode> iterator(){
        return new IndexIterator<>(this);
    }
    @Override
    public int size(){
        return mChildes.size();
    }
    @Override
    public XMLNode get(int index){
        return mChildes.get(index);
    }
    public void addAll(Iterable<? extends XMLNode> iterable){
        Iterator<? extends XMLNode> itr = iterable.iterator();
        while (itr.hasNext()){
            add(itr.next());
        }
    }
    public void add(XMLNode xmlNode) {
        if(xmlNode == null || xmlNode == this){
            return;
        }
        synchronized (this){
            if(mChildes == EMPTY){
                mChildes = new ArrayList<>();
            }
            mChildes.add(xmlNode);
            xmlNode.setParent(this);
            if(mChildes.size() - lastTrimSize > TRIM_INTERVAL){
                mChildes.trimToSize();
                lastTrimSize = mChildes.size();
            }
        }
    }
    public boolean remove(XMLNode xmlNode){
        synchronized (this){
            if(xmlNode != null && mChildes.remove(xmlNode)){
                xmlNode.setParent(null);
                return true;
            }
            return false;
        }
    }
    @Override
    public void serialize(XmlSerializer serializer) throws IOException {
        startSerialize(serializer);
        serializeChildes(serializer);
        endSerialize(serializer);
    }
    abstract void startSerialize(XmlSerializer serializer) throws IOException;
    private void serializeChildes(XmlSerializer serializer) throws IOException {
        Iterator<XMLNode> itr = iterator();
        while (itr.hasNext()){
            itr.next().serialize(serializer);
        }
    }
    abstract void endSerialize(XmlSerializer serializer) throws IOException;

    XMLElement newElement(){
        return new XMLElement();
    }
    XMLText newText(){
        return new XMLText();
    }
    XMLComment newComment(){
        return new XMLComment();
    }
    XMLAttribute newAttribute(){
        return new XMLAttribute();
    }

    private static final int TRIM_INTERVAL = 1000;
    private static final ArrayList<XMLNode> EMPTY = new ArrayList<>(1);
}
