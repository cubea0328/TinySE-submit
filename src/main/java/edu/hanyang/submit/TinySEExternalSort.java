package edu.hanyang.submit;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Pair;

import edu.hanyang.indexer.ExternalSort;

public class TinySEExternalSort implements ExternalSort {
	
	class Tuple implements Comparable<Tuple> {
		public int idx;
		public MutableTriple< Integer, Integer, Integer> X;
				
		public Tuple(MutableTriple< Integer, Integer, Integer> X, int index) {
			this.X = X;
			this.idx = index;
		}

		public void setTuple(MutableTriple< Integer, Integer, Integer> X) {
			this.X = X;
		}
		
		@Override
		public int compareTo(Tuple o) {
			return X.compareTo(o.X);
		}
	}

	class triplesort implements Comparator< MutableTriple<Integer,Integer,Integer> > {
		@Override
		public int compare(MutableTriple<Integer, Integer, Integer> o1, MutableTriple<Integer, Integer, Integer> o2) {
			if(o1.getLeft() < o2.getLeft())
				return -1;
			else if(o1.getLeft() > o2.getLeft())
				return 1;
			else {
				if(o1.getMiddle() < o2.getMiddle())
					return -1;
				if(o1.getMiddle() > o2.getMiddle())
					return 1;
				else {
					if(o1.getRight() < o2.getRight())
						return -1;
					else if(o1.getRight() > o2.getRight())
						return 1;
					else return 0;
				}
			}
		}
		
	}
	
	public void sort(String infile, String outfile, String tmpdir, int blocksize, int nblocks) throws IOException {
		// complete the method
		
		File tmpfile = new File(tmpdir);
		File file = new File(infile);
		File initfile = new File(tmpdir + "/init");
			
		tmpfile.mkdir();
		initfile.mkdir();
		
		DataInputStream is = new DataInputStream( new BufferedInputStream(
					new FileInputStream(infile), blocksize
				)
			);
		
		File tmpFile;
		String runs;
		DataOutputStream tmpOs;
		triplesort ts = new triplesort();
		
		int entryCnt = blocksize / 12;
		blocksize = entryCnt * 12;
		int entryCntBlock = 30000;

		int fileNum = (int) (file.length() / (entryCntBlock*12))+1;
		Long fileSize = file.length();
		
		for(int index = 0; index < fileNum; ++index) {
			
			ArrayList< MutableTriple<Integer,Integer,Integer> > list = new ArrayList< MutableTriple<Integer,Integer,Integer> >(30000);
			runs =  tmpdir + "/init/runs_" + (index < 10 ? "0" + index : index) + ".data";
			tmpFile = new File(runs);
			tmpOs = new DataOutputStream( new BufferedOutputStream (
						new FileOutputStream(tmpFile), blocksize)
					);
			for(int cnt = 0; cnt < entryCntBlock && fileSize > 0; ++cnt,fileSize -= 12) {
				try {
					list.add(MutableTriple.of(is.readInt(), is.readInt(), is.readInt()));
				}
				catch(EOFException e) {
					break;
				}
				
			}
			list.sort(ts);
			for(MutableTriple<Integer,Integer,Integer> e : list) {
				try {
					tmpOs.writeInt(e.getLeft());
					tmpOs.writeInt(e.getMiddle());
					tmpOs.writeInt(e.getRight());
				}
				catch (EOFException x) {
                    break;
                }
			}
			
			tmpOs.flush();
			tmpOs.close();

			list.clear();
		}
		mergingAll(fileNum, blocksize, nblocks, tmpdir, outfile);
		is.close();
	}
	

	public void mergingAll(int runsCount, int blocksize, int nblocks , String tmpdir, String outfile) throws IOException {
		
		int nextWayNum;
		
		
		for(int pre = runsCount, nextFileNum = 0, step = 0; pre > 1; nextFileNum = 0, step++) {
			
			List<String> list = new ArrayList<String>();
			
			int nowRuns = pre;
			int out_idx = 0;
			int stackFileNum = 0;
			
			File stepdir = new File(tmpdir + "/" + (step+1));
			stepdir.mkdir();
			
			while(pre > 0) {
				nextWayNum = nblocks-1;
				
				if(pre < nextWayNum) nextWayNum = pre;
				
				for(int idx = 0; idx < nextWayNum; idx++)
					list.add(tmpdir + "/" + (step == 0 ? "init" : step )  + "/runs_" + ((idx + stackFileNum) < 10 ? "0" : "") + (idx + stackFileNum) + ".data");
				
				String out = tmpdir + "/" + (step+1) + "/runs_" + (out_idx < 10 ? "0" : "") + (out_idx++) + ".data";
				
				if(nowRuns == nextWayNum)
					out = outfile;
				
				nextFileNum++;
				stackFileNum += nextWayNum;
				pre -= nextWayNum;
				
				NWayMerge(blocksize, nblocks, list, out);
				list.clear();
			}
			
			pre = nextFileNum;
			
		}
		
	}
	
	public void NWayMerge(int blocksize, int nblocks, List<String> input, String out) throws IOException {
		
		File outfile = new File(out);
		PriorityQueue<Tuple> pq = new PriorityQueue<Tuple>();
		HashMap<Integer, Pair<DataInputStream, Long>> islist = new HashMap<Integer, Pair<DataInputStream, Long>>();
		DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outfile), blocksize));
		
		File tmpfile;
		String fileName;
		DataInputStream tempis;
		int inputSize = input.size();
		for(int index = 0; index < inputSize; index++) {
			fileName = input.get(index);
			tmpfile = new File(fileName);
			
			if(tmpfile.exists()) {
				tempis = new DataInputStream(new BufferedInputStream(new FileInputStream(tmpfile), blocksize));
				pq.add(new Tuple(MutableTriple.of(tempis.readInt(), tempis.readInt(), tempis.readInt()), index));
				islist.put(index, Pair.of(tempis, tmpfile.length()-12));
			}
		}
		
		Tuple tempTuple;
		while(!pq.isEmpty()) {
			
			tempTuple = pq.poll();
			
			os.writeInt(tempTuple.X.getLeft());
			os.writeInt(tempTuple.X.getMiddle());
			os.writeInt(tempTuple.X.getRight());
			
			
			
			if(islist.containsKey(tempTuple.idx) && islist.get(tempTuple.idx).getRight() > 0) {
				tempTuple.setTuple(
						MutableTriple.of(
								islist.get(tempTuple.idx).getLeft().readInt(),
								islist.get(tempTuple.idx).getLeft().readInt(),
								islist.get(tempTuple.idx).getLeft().readInt()
						)
				);
				islist.replace(tempTuple.idx, Pair.of(islist.get(tempTuple.idx).getLeft(), islist.get(tempTuple.idx).getRight() - 12));
				pq.add(tempTuple);
			}
		}
		
		os.flush();
		os.close();
	}

}