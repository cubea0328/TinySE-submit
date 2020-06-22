package edu.hanyang.submit;

import java.io.*;
import java.util.*;
import edu.hanyang.indexer.*;
import edu.hanyang.indexer.QueryPlanTree.QueryPlanNode;
import edu.hanyang.indexer.QueryPlanTree.NODE_TYPE;

public class TinySEQueryProcess implements QueryProcess {

	@Override
	public void op_and_w_pos(DocumentCursor op1, DocumentCursor op2, int shift, IntermediatePositionalList out)
			throws IOException {
		
		
		while(!op1.is_eol() && !op2.is_eol()) {
			int op1docid = op1.get_docid();
			int op2docid = op2.get_docid();
			
			if(op1docid < op2docid)
				op1.go_next();
			else if(op1docid > op2docid)
				op2.go_next();
			else {
				PositionCursor op1cur = op1.get_position_cursor();
				PositionCursor op2cur = op2.get_position_cursor();
				while(!op1cur.is_eol() && !op2cur.is_eol()) {
					
					int op1curValue = op1cur.get_pos();
					int op2curValue = op2cur.get_pos();
					
					if(op1curValue + shift < op2curValue)
						op1cur.go_next();
					else if (op1curValue + shift > op2curValue)
						op2cur.go_next();
					else {
						out.put_docid_and_pos(op1docid , op1curValue);
						op1cur.go_next();
						op2cur.go_next();
					}
				}
				op1.go_next();
				op2.go_next();
			}
		}
	}
	
	@Override
	public void op_and_wo_pos(DocumentCursor op1, DocumentCursor op2, IntermediateList out) throws IOException {
		
		while(!op1.is_eol() && !op2.is_eol()) {
			int op1docid = op1.get_docid();
			int op2docid = op2.get_docid();
			
			if(op1docid < op2docid)
				op1.go_next();
			else if(op1docid > op2docid)
				op2.go_next();
			else {
				out.put_docid(op1docid);
				op1.go_next();
				op2.go_next();
			}
		}
	}

	@Override
	public QueryPlanTree parse_query(String query, StatAPI stat) throws Exception {
		
		int index = 0;
		String temp = "";
		char[] queryArray = query.toCharArray();
		QueryPlanTree tree = new QueryPlanTree();
		
		for(int i = 0; i < query.length(); i++) {
			if(queryArray[i] == ' ') {
				
				if(temp.length() > 0) {
					
					int tempInt = Integer.parseInt(temp);
					temp = "";
					QueryPlanNode node = tree.new QueryPlanNode();
					node.left = tree.new QueryPlanNode();
					node.left.termid = tempInt;
					node.left.type = NODE_TYPE.OPRAND;
					node.type = NODE_TYPE.OP_REMOVE_POS;
					
					if(tree.root == null)
						tree.root = node;
					else {
						
						QueryPlanNode newnode = tree.new QueryPlanNode();
						newnode.left = tree.root;
						newnode.right = node;
						newnode.type = NODE_TYPE.OP_AND;
						tree.root = newnode;
					}
				} else {
					
					QueryPlanNode node = tree.new QueryPlanNode();
					node.left = tree.root;
					node.type = NODE_TYPE.OP_AND;
					tree.root = node;
				}
			}
			else if(queryArray[i] == '\"')
				i = in_phrase_parsing(i+1, query, tree);
			else {
				temp += queryArray[i];
				
				if(i == query.length()-1 && temp.length() > 0) {
					QueryPlanNode node = tree.new QueryPlanNode();
					node.left = tree.new QueryPlanNode();
					node.left.termid = Integer.parseInt(temp);
					node.left.type = NODE_TYPE.OPRAND;
					node.type = NODE_TYPE.OP_REMOVE_POS;
					
					if(tree.root == null)
					{  tree.root = node; }
					else if(tree.root.left != null && tree.root.right == null && tree.root.type == NODE_TYPE.OP_AND) {
						tree.root.right = node;
					}
					else {
						
						QueryPlanNode newnode = tree.new QueryPlanNode();
						newnode.left = tree.root;
						newnode.right = node;
						newnode.type = NODE_TYPE.OP_AND;
						tree.root = newnode;
					}
				}
			}
		}
		
		return tree;
	}
	
	public void readTree(QueryPlanNode node) {
		
		if(node != null) {
			System.out.println("\t" + node.type + ", " + node.termid + ", " + node.shift);
			
			if(node.left != null) {
					readTree(node.left);
			}
			if(node.right != null) {
				readTree(node.right);
			}
		}
		
	}
	
	public int in_phrase_parsing(int idx, String query, QueryPlanTree tree) {
		
		int shift = 0;
		int retIndex = 0;
		String temp = "";
		char[] queryArray = query.toCharArray();
		
		QueryPlanNode inproot = tree.new QueryPlanNode();
		
		for(int i = idx; i < query.length(); i++) {
			if(queryArray[i] == '\"') {
				
				QueryPlanNode node = tree.new QueryPlanNode();
				node.type = NODE_TYPE.OPRAND;
				node.termid = Integer.parseInt(temp);
				
				if(inproot.left == null)
					inproot = node;
				else inproot.right = node;
				
				QueryPlanNode newroot = tree.new QueryPlanNode();
				newroot.left = inproot;
				newroot.type = NODE_TYPE.OP_REMOVE_POS;
				
				if(tree.root == null)
					tree.root = newroot;
				else tree.root.right = newroot;
				
				retIndex = i;
				break;
			}
			else if(queryArray[i] == ' ') {
				
				if(!temp.equals("")) {
					QueryPlanNode node = tree.new QueryPlanNode();
					
					if(inproot.left == null) {
						node.left = tree.new QueryPlanNode();
						node.left.termid = Integer.parseInt(temp);
						node.left.type = NODE_TYPE.OPRAND;
						node.type = NODE_TYPE.OP_SHIFTED_AND;
						node.shift = ++shift;
						inproot = node;
					}
					else if(inproot.right == null) {
						
						inproot.right = tree.new QueryPlanNode();
						inproot.right.termid = Integer.parseInt(temp);
						inproot.right.type = NODE_TYPE.OPRAND;
						
						node.left = inproot;
						node.type = NODE_TYPE.OP_SHIFTED_AND;
						node.shift = ++shift;
						
						inproot = node;
						
					}
					
					temp = "";
				}
				
			}
			else temp += queryArray[i];
		}
		
		return retIndex;
	}
}
