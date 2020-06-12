package edu.hanyang.submit;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.tartarus.snowball.ext.PorterStemmer;

import edu.hanyang.indexer.Tokenizer;

public class TinySETokenizer implements Tokenizer {

	SimpleAnalyzer analyzer;
	PorterStemmer stemmer;
	
	public void setup() {
		analyzer =new SimpleAnalyzer();
		stemmer = new PorterStemmer();
	}

	public List<String> split(String text) {
		ArrayList stemmed = new ArrayList();
		try {
			TokenStream stream=analyzer.tokenStream(null, text);
			stream.reset();
			CharTermAttribute term=stream.getAttribute(CharTermAttribute.class);
			while(stream.incrementToken()) {
				stemmer.setCurrent(term.toString());
				stemmer.stem();
				stemmed.add(stemmer.getCurrent());
				System.out.println(stemmer.getCurrent());
			}
			stream.close();
		}catch(IOException e){
			throw new RuntimeException(e);
		}
		return stemmed;
	}

	public void clean() {
		analyzer.close();
		/*
		System.out.println("starting test module 2");
		ClassLoader classLoader = this.getClass().getClassLoader();
		File infile = new File(classLoader.getResource("test.data").getFile());
		try {
			DataInputStream is=new DataInputStream(
					new BufferedInputStream(
							new FileInputStream(infile),1024)
					);
			int len=(int)infile.length();
			for(int i=0;i<len/300000;i++) {
				System.out.println(is.readByte());
			}
		}catch(IOException e) {
			
		}
		*/
		
	}

}