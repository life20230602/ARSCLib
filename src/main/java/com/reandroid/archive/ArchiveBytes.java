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
package com.reandroid.archive;

import com.reandroid.archive.io.ArchiveByteEntrySource;
import com.reandroid.archive.io.ZipByteInput;
import com.reandroid.utils.io.IOUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ArchiveBytes extends Archive<ZipByteInput>{
    public ArchiveBytes(ZipByteInput zipInput) throws IOException {
        super(zipInput);
    }
    public ArchiveBytes(byte[] array) throws IOException {
        super(new ZipByteInput(array));
    }
    public ArchiveBytes(InputStream inputStream) throws IOException {
        this(IOUtil.readFully(inputStream));
    }
    @Override
    InputSource createInputSource(ArchiveEntry entry){
        return new ArchiveByteEntrySource(getZipInput(), entry);
    }
    @Override
    void extractStored(File file, ArchiveEntry archiveEntry) throws IOException {
        File dir = file.getParentFile();
        if(dir != null && !dir.exists()){
            dir.mkdirs();
        }
        InputStream inputStream = getZipInput().getInputStream(archiveEntry.getFileOffset(),
                archiveEntry.getDataSize());
        FileOutputStream outputStream = new FileOutputStream(file);
        IOUtil.writeAll(inputStream, outputStream);
        outputStream.close();
        inputStream.close();
    }
}
