package edu.hanyang.submit;

import edu.hanyang.indexer.BPlusTree;

import java.io.*;
import java.util.*;
import java.nio.ByteBuffer;

public class TinySEBPlusTree implements BPlusTree{

	int blockSize;
	int nblocks;
	int cacheSize;
	int fanout;
	int mode;
	int depth;
	int cur;
	int nodecnt;
	
	int rootPos;
	Node root;
	LRU cache;
	float adjust;
	
	static byte[] bytes;
	static ByteBuffer buffer;

	String treepath;
	String metapath;
	RandomAccessFile tree;
	RandomAccessFile meta;
	
	public class LRU{
		class Data {
	        int key;
	        Node node;
	        public Data(int key, Node node){
	            this.key = key;
	            this.node = node;
	        }
	    }
		int mode;
		int capacity;
		LinkedHashMap<Integer, Data> map;
		
		public LRU(int capacity, int mode) {
			this.capacity=capacity;
			this.mode = mode;
			this.map = new LinkedHashMap<Integer, Data>(capacity,.75f,true) {
				@Override protected boolean removeEldestEntry(Map.Entry<Integer, Data> eldest) {
					if(size() == capacity)
						try {
							LRU.this.RemoveNode(eldest);
						} catch(IOException e) {
							
						}
					return size() > LRU.this.capacity;
				}
			};
		}
		
		public void RemoveNode(Map.Entry<Integer, Data> entry) throws IOException{
			int key = entry.getKey();
			Node node = entry.getValue().node;
			map.remove(key);
			if(mode == 1) node.writeData(node.pos);
		}
		
		public Node GetNode(int key) throws IOException {
	        if(map.containsKey(key))
	            return map.get(key).node;
	        else {
	            Node node = MakeNode(key);
	            SetNode(key, node);
	            return node;
	        }
	    }
		public void SetNode(int key, Node value) throws IOException {
			if(map.containsKey(key))
				map.get(key);
			else {
				Data n = new Data(key,value);
				map.put(key,n);
			}
		}
		
		public void Flush() throws IOException{
			map.forEach((key,value)->{
				try {
					value.node.writeData(value.node.pos);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}
	}
	
	class Node {

		int keySize;
		int pos;
		int[] keys;
		int[] values;
		boolean isLeaf;
		
		Node(boolean isLeaf, int pos) throws IOException {

			this.isLeaf = isLeaf;
			this.keys = new int[fanout-1];
			this.values = new int[fanout];
			if(pos==-1) {
				this.pos = nodecnt * blockSize;
				this.keySize = 0;
			}
			else {
				this.pos = pos;
				tree.seek(this.pos);
				tree.read(bytes);
				   
				buffer.clear();
				buffer.getInt();
				this.keySize = buffer.getInt();
				
				for (int idx = 0; idx < keySize; ++idx) {
					this.keys[idx] = buffer.getInt();
					this.values[idx] = buffer.getInt();
				}
				
				if (!isLeaf && keySize != 0)
					this.values[keySize] = buffer.getInt();
			}
		}
		
		public boolean nodeIsFull() {
			return keySize == fanout-1;
		}
		
		public void writeData(int offset) throws IOException {
		
			buffer.clear();
			buffer.putInt(this.isLeaf ? 1 : 0);
			buffer.putInt(this.keySize);
			
			for (int idx = 0; idx < this.keySize; ++idx) {
				buffer.putInt(this.keys[idx]);
				buffer.putInt(this.values[idx]);
			}
				
			if (!isLeaf && keySize > 0)
				buffer.putInt(this.values[keySize]);
			
			tree.seek(this.pos);
			tree.write(bytes);
		}

		public int Search(int key) throws IOException {
			
			if(this.isLeaf) {
				int pos = Arrays.binarySearch(keys, 0, keySize, key);
				return pos >= 0 ? values[pos] : -1;
			}
			else {
				Node node = GetChild(key, 0);
				return node.Search(key);
			}
		}

		public int Split() throws IOException {

			nodecnt++;
			Node newNode = new Node(this.isLeaf,-1);
			
			int start = keySize/2;
			int end = keySize;
			
			int leafInt = this.isLeaf ? 0 : 1;
			for(int idx = 0; idx < end - start - leafInt; ++idx)
				newNode.keys[idx] = keys[idx + start + leafInt];
			
			newNode.keySize = end - (start + leafInt);
			this.keySize -= end - (start + leafInt);
			
			for(int idx = 0; idx < end - start; ++idx)
				newNode.values[idx] = values[idx + start + leafInt];
			
			cache.SetNode(newNode.pos, newNode);
			return keys[start];
		}
		
		public void InsertValue(int key, int value) throws IOException {
			if(this.isLeaf) {
				
				int pos = Arrays.binarySearch(keys ,0 ,keySize, key);
				int valueIndex = pos >= 0 ? pos : -pos - 1;
				
				for(int idx = this.keySize - 1; idx >= valueIndex; idx--){
					this.keys[idx + 1] = this.keys[idx];
					this.values[idx + 1] = this.values[idx];
				}
				   
				this.keySize++;
				this.keys[valueIndex] = key;
				this.values[valueIndex] = value;
				cache.SetNode(this.pos, this);
			    
			} else {
				Node childNode = GetChild(key, 1);
				childNode.InsertValue(key, value);
				    
				if (childNode.nodeIsFull()) {
					int leftdata = childNode.Split();
					int pos = nodecnt * blockSize;
					InsertNode(leftdata, pos);
					cache.SetNode(this.pos, this);
				}
			}
		
			if( root.nodeIsFull() ) {
				nodecnt++;
				Node newRootNode = new Node(false,-1);
				newRootNode.keys[0] = Split();
				newRootNode.keySize++;
				newRootNode.values[0] = this.pos;
				newRootNode.values[1] = nodecnt * blockSize;
				root = newRootNode;
			}
		}
		
		public void InsertNode(int key, int childOffset) {
			
			int pos = Arrays.binarySearch(keys,0,keySize, key);
			int childIndex = pos >= 0 ? pos + 1 : -pos - 1;
			
			for(int idx = keySize-1; idx >= childIndex; --idx){
				this.keys[idx + 1] = this.keys[idx];
				this.values[idx + 2] = this.values[idx + 1];
			}
			
			this.keySize++;
			this.keys[childIndex] = key;
			this.values[childIndex + 1] = childOffset;
		    
		}
		
		Node GetChild(int key, int isInsert) throws IOException {
			 
			int pos = Arrays.binarySearch(this.keys,0, this.keySize, key);
			int childIndex = pos >= 0 ? ( pos + 1 ): (-pos - 1);
			int childValue = this.values[childIndex];
			return cache.GetNode(childValue);
		    
		}
	}
	
	@Override
	public void close() throws IOException {
		
		root.writeData(root.pos);
		cache.Flush();
		tree.close();
		
		meta.writeInt(root.pos);
		meta.writeInt(fanout);
		meta.writeInt(blockSize);
		meta.close();
	}

	@Override
	public void insert(int key, int value) throws IOException {
		root.InsertValue(key, value);
	}

	@Override
	public void open(String metapath, String treepath, int blockSize, int nblocks) throws IOException {
		
		this.treepath = treepath;
		this.metapath = metapath;
		int adjust = (blockSize*nblocks) / 2048000;
		blockSize=(int)(blockSize/adjust);
		nblocks=(int)(nblocks*adjust);
		
		File tmpdir = new File("./tmp");
		File treeFile = new File(this.treepath);
		File metaFile = new File(this.metapath);
		
		if (!tmpdir.exists())
			tmpdir.mkdir();
		if(!treeFile.exists())
			treeFile.createNewFile();
		
		this.meta = new RandomAccessFile(metaFile, "rw");
		this.tree = new RandomAccessFile(treeFile, "rw");
		
		if(metaFile.exists() && metaFile.length() > 0){
			this.mode = 0;
			this.rootPos = this.meta.readInt();
			this.fanout = this.meta.readInt();
			this.blockSize = this.meta.readInt();
		}
		else{
			this.mode = 1;
			metaFile.createNewFile();
			this.rootPos = 0;
			this.fanout = blockSize/(Integer.SIZE/4)-1;
			this.blockSize = blockSize;
		}
		
		cache = new LRU(nblocks, this.mode);
		
		if(tree.length() > 0)
			root = MakeNode(rootPos);
		else root = new Node(true,-1);

		bytes = new byte[this.blockSize];
		buffer = ByteBuffer.wrap(bytes);
	}

	@Override
	public int search(int key) throws IOException {
		return root.Search(key);
	}
	
	public void init() {
		
	}

	Node MakeNode(int offset) throws IOException{
		this.tree.seek(offset);
		int isLeaf = tree.readInt();
		return new Node(isLeaf == 1, offset);
	}
}