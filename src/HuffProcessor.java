import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 * 
 * Spencer Rosen - sjr63 and Ben Litvin bdl32
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in); 
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root); 
		out.writeBits(BITS_PER_INT, HUFF_TREE); 
		writeHeader(root,out);
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}

	/**Writes the tree used to decode the huffman compression using a preorder traversal
	 * 
	 * @param root - the root of the tree with the Huffman key
	 * 
	 * @param out - the BitStream written to, the output file
	 */
	private void writeHeader(HuffNode root, BitOutputStream out){
		if(root == null) return;
		if(root.myLeft== null&&root.myRight== null){
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD +1, root.myValue);
		}
		else{
			out.writeBits(1, 0);
		}
		writeHeader(root.myLeft, out);
		writeHeader(root.myRight, out);
	}

	
	/**Using the key already established writing the new bit sequences into the output file
	 * 
	 * @param codings - an array of Strings with the codings for all the bit sequences 
	 * 
	 * @param in - Buffered bit stream of the file to be compressed.
	 * 
	 * @param out -  Buffered bit stream writing to the output file.
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out){
		while(true){
			int bit = in.readBits(BITS_PER_WORD);
			if(bit == -1){
				break;
			}else{
				String code = codings[bit];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
			}
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));
	}

	
	/**Reads the file for a count of how many of each character there are.
	 * 
	 * 
	 * @param in - Buffered bit stream of the file to be compressed.
	 * 
	 */
	private int[] readForCounts(BitInputStream in){
		int[] arr = new int[ALPH_SIZE +1];
		while(true){
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1){
				break;
			}else{
				arr[bits]+=1;
			}
		}
		arr[PSEUDO_EOF] = 1;
		return arr;
	}

	/**Calls a recursive helper method to create the codings for each character
	 * 
	 * @param root - the root of the tree with the Huffman key
	 * 
	 */
	private String[] makeCodingsFromTree(HuffNode root){
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}

	/**The helper method that recursively assigns the characters their sequences
	 * 
	 * @param root - the root of the tree
	 * 
	 * @param path - an empty string that will be assigned the new bit sequence of the character 
	 * 
	 * @param enc - the array of Strings where the sequences will be stored
	 */
	private void codingHelper(HuffNode root, String path, String[] enc){
		if(root == null){
			return;
		}
		if(root.myLeft== null && root.myRight == null){
			enc[root.myValue] = path;
			return;
		}
		if(root.myLeft!= null){
			codingHelper(root.myLeft, path+"0", enc);
		}
		if(root.myRight!= null){
			codingHelper(root.myRight, path + "1", enc);
		}
	}

	/**Makes a tree from the counts of the characters in the text file
	 * 
	 * @param arr - Array of Integers - the number of times each character is used in the text file
	 * 
	 */
	private HuffNode makeTreeFromCounts(int[] arr){
		PriorityQueue<HuffNode> pq= new PriorityQueue<>();
		for(int i = 0; i < arr.length; i++){
			if(arr[i]>0){
				pq.add(new HuffNode(i, arr[i],null, null));
			}
		}
		while(pq.size()>1){ //uses the priority queue to make the tree by removing from the queue
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode make = new HuffNode(0, left.myWeight+ right.myWeight, left, right);
			pq.add(make); //adds the new node back to the priority queue
		}

		HuffNode root = pq.remove(); //takes the first node, the root from the priority queue
		return root;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if(bits!=HUFF_TREE){
			throw new HuffException("illegal header starts with "+ bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}

	/** Reads the tree necessary to decode the compressed file
	 * 
	 * @param in - Buffered bit stream of the file to be compressed.
	 * 
	 */
	private HuffNode readTreeHeader(BitInputStream in){
		int bit = in.readBits(1);
		if(bit == -1){
			throw new HuffException("Illegal bit" + bit);
		}
		if(bit == 0){
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0, left, right);
		}else{
			int val = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(val,0,null,null);
		}
	}

	/**Reads the compressed bits and decodes them from the input file to create the final output file 
	 * 
	 * @param root - the root of the tree used as the key to decode the compressed file
	 * 
	 * @param in - Buffered bit stream of the file to be compressed.
	 * 
	 * @param out - Buffered bit stream writing to the output file.
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out){

		HuffNode current = root;
		while(true){
			int bits = in.readBits(1);
			if(bits==-1){
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else{
				if(bits == 0){
					current = current.myLeft;
				}
				else{
					current = current.myRight;
				}
				if(current.myLeft ==null && current.myRight==null){
					if(current.myValue == PSEUDO_EOF){
						break;
					}
					else{
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}