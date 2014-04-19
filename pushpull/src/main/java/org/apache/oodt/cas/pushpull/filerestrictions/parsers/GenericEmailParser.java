/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.oodt.cas.pushpull.filerestrictions.parsers;

//OODT imports
import org.apache.oodt.cas.pushpull.filerestrictions.Parser;
import org.apache.oodt.cas.pushpull.filerestrictions.VirtualFile;
import org.apache.oodt.cas.pushpull.filerestrictions.VirtualFileStructure;
import org.apache.oodt.cas.pushpull.exceptions.ParserException;

//Google imports
import com.google.common.collect.Lists;

//JDK imports
import java.io.FileInputStream;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A generic email parser which generates file paths to be downloaded by using a defined java
 * Pattern. The pattern should specify pattern groups for file paths in the matching pattern.
 * These groups will then be extracted and added to the file structure.
 *
 * @author bfoster@apache.org (Brian Foster)
 */
public class GenericEmailParser implements Parser {

  public static final String FILE_PATTERNS_PROPERTY_NAME =
      "org.apache.oodt.cas.pushpull.generic.email.parser.file.pattern";

  private final String filePattern;

  public GenericEmailParser() {
    filePattern = loadFilePattern();
  }
 
  public GenericEmailParser(String filePattern) {
    this.filePattern = filePattern;
  }

  @Override
  public VirtualFileStructure parse(FileInputStream emailFile) throws ParserException {
    VirtualFile root = VirtualFile.createRootDir();

    List<String> filePaths = generateFilePaths(readEmail(emailFile));

    for (String filePath : filePaths) {
      new VirtualFile(root, filePath, false);
    }

    return new VirtualFileStructure(null, "/", root);
  }

  private String readEmail(FileInputStream emailFile) {
    StringBuilder emailText = new StringBuilder("");
    Scanner scanner = new Scanner(emailFile);
    while (scanner.hasNextLine()) {
      emailText.append(scanner.nextLine()).append("\n");
    }
    scanner.close();
    return emailText.toString();
  }

  private List<String> generateFilePaths(String emailText) throws ParserException {
    List<String> filePaths = Lists.newArrayList();
    Pattern pattern = Pattern.compile(filePattern);
    Matcher m = pattern.matcher(emailText);
    if (m.find()) {
      // Ignore index 0, as that is the matching string for pattern.
      for (int i = 1; i <= m.groupCount(); i++) {
        filePaths.add(m.group(i));          
      }
    }
    return filePaths;
  }

  private String loadFilePattern() {
    return System.getProperty(FILE_PATTERNS_PROPERTY_NAME);
  }
}
