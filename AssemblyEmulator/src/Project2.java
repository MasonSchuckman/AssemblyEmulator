import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;

import javax.print.attribute.SetOfIntegerSyntax;

interface Instruction{
	void execute(String [] params);
}

public class Project2 {

	//global variables
	static HashMap <String, Instruction> instructionMap; //contains the instructions of the code (.text section)
	static HashMap <String, byte[]> dataMap; //used to convieniently address different parts of memory.
	
	static byte [][] registers = new byte[6][]; //each register can hold 8 bytes. (5 general purpose + 1 return address register)
	static ArrayList<byte []> variables; //created when .data section is parsed
	static int ip = 0; 			//instruction pointer
	static int flag = 0;
	static Stack<Integer> stack = new Stack<Integer>();


	public static void main(String [] args) {
		for(int i = 0; i < 6; i++)
			registers[i] = new byte[8];

		//use a map for easy calling of the instructions
		instructionMap = new HashMap<String, Instruction>();		
		setupInstructionMap(instructionMap);

		//simmilarly, we use a map for addressing the memory (registers and variables made in .data).
		dataMap = new HashMap<String, byte[]>();
		setupDataMap(dataMap);

		//test();

		String dir = "C:\\Users\\suprm\\Desktop\\341 submit\\313\\test.txt";
		File f = new File(dir);
		
		String [][] instructions = null;
		try {
			parseData(f);
			instructions = parseInstructions(f);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		runProgram(instructions, null, instructionMap);
	}

	public static void test() {
		registers[0][1] = 4;
		registers[1][2] = 5;
		String [] ins = {"mov", "r1", "r2"};
		mov.execute(ins);

		for(byte [] d : registers)
			System.out.println(Arrays.toString(d));
		System.out.println(); 
	}

	public static void runProgram(String [][] instructions, byte [][] data, HashMap <String, Instruction> instructionMap) {
		for(ip = 0; ip < instructions.length; ip++) {
			System.out.print(ip + "\t");
			for(String s : instructions[ip])
				System.out.print(s + " ");
			System.out.println();
			Instruction p = instructionMap.get(instructions[ip][0]);
			if(p != null)
				p.execute(instructions[ip]);
		}
	}

	public static void setupInstructionMap(HashMap<String, Instruction> map) {
		map.put("mov", mov);
		map.put("add", add);
		map.put("sub", sub);
		map.put("jmp", jmp);
		map.put("cmp", cmp);
		map.put("jne", jnq);
		map.put("add", jeq);
		map.put("syscall", syscall);
	}

	public static void setupDataMap(HashMap<String, byte[]> map) {
		map.put("r1", registers[0]);
		map.put("r2", registers[1]);
		map.put("r3", registers[2]);
		map.put("r4", registers[3]);
		map.put("r5", registers[4]);

		map.put("lr", registers[5]);
	}

	//iterate through the first chunks of the line (this is all we'd ever care about. any more than 3, and its a comment or something)
	public static String [][] parseInstructions(File f) throws FileNotFoundException {
		Scanner sc = new Scanner(f);
		int lines = countLines(f);

		String [][] instructions = new String[lines][3];
		String s = null;
		int counter = 0;

		//we only want to read stuff that's in .data
		boolean inData = false; 


		while (sc.hasNextLine() && (s = sc.nextLine()) != null){
			//trim whitespace
			s = s.trim();			

			//split the line up by spaces and commas. this is to isolate instructions and their parameters
			String [] lineArgs = s.split("[\\s,]+");

			//make sure we're in the .text section
			if(checkSection(lineArgs) == 0)
				inData = true;
			else {
				if(checkSection(lineArgs) != -1)
					inData = false;
			}


			if(inData) {
				//check if this line is valid
				if(getInstructionCode(lineArgs[0]) != -1) {

					//save only the first 3 args
					String [] first3Args = new String[3];
					for(int i = 0; i < Math.min(3, lineArgs.length); i++) 
						first3Args[i] = lineArgs[i];

					//save this line's code
					instructions[counter] = first3Args;
					System.out.println(Arrays.toString(lineArgs));
					counter++;
				}
				//output = output.concat(s + "\n");

			}
		}
		instructions = condense(instructions);
		System.out.println("-----------------");
		for(int i = 0; i < instructions.length; i++) {
			System.out.println(Arrays.toString(instructions[i]));
		}
		sc.close();

		return instructions;
	}
	
	//gets the variables for the program
	public static String [][] parseData(File f) throws FileNotFoundException {
		Scanner sc = new Scanner(f);
		int lines = countLines(f);

		String [][] data = new String[lines][1024];
		String s = null;
		int counter = 0;

		//we only want to read stuff that's in .data
		boolean inVariables = false; 


		while (sc.hasNextLine() && (s = sc.nextLine()) != null){
			//trim whitespace
			s = s.trim();			

			//split the line up by spaces and commas. this is to isolate instructions and their parameters
			String [] lineArgs = s.split("[\\s,]+");

			//make sure we're in the .text section
			if(checkSection(lineArgs) == 2)
				inVariables = true;
			else
				//this will trigger if we hit a "section" followed by something other than what we want.
				if(checkSection(lineArgs) != -1)
					inVariables = false;
			


			if(inVariables) {
				//check if this line is valid
				if(getInstructionCode(lineArgs[0]) != -1) {

					//save only the first 4 args (label, size, text/data, possible extra newline at the end)
					String [] first4Args = new String[1024];
					for(int i = 0; i < Math.min(1024, lineArgs.length); i++) { 
						if(lineArgs[i].equals(";"))
							break;
						first4Args[i] = lineArgs[i];
					}

					//save this line's code
					data[counter] = first4Args;
					System.out.println(Arrays.toString(lineArgs));
					counter++;
				}
				//output = output.concat(s + "\n");

			}
		}
		
		data = condense(data);
		
		System.out.println("-----------------");
		for(int i = 0; i < data.length; i++) {
			System.out.println(Arrays.toString(data[i]));
		}
		sc.close();
		
		storeData(data);
		
		return data;
	}
	
	public static void storeData(String [][] lines) {
		variables = new ArrayList<byte []>();
		
		for(int i = 0; i < lines.length; i++) {
			String [] line = lines[i];
			String name = line[0].substring(0, line[0].length() - 1); //use substring to cut off the ":"
			String length = line[1];
			//String data = line[2];
			String data = "";
			
			boolean quotes = false;
			for(int j = 2; j < 1024; j++) {
				if(line[j] == null)
					break;
				if(line[j].startsWith("\"")) {
					quotes = true;
				}
				if(quotes) {
					data += line[j].replace('\"', ' ') + " ";
					if(line[j].endsWith("\"")) {
						quotes = false;
					}
				}
				if(!quotes && line[j].contains(";"))
					break;
			}
			data = data.trim();
			
			
			boolean hasNewLine = (line[3].equals("10") ? true : false);
			
			//first, we need to convert the string length to an int
//			switch(length) {
//			case "
//			}
			
			byte [] var = new byte[64];
			var = data.getBytes();
			
			variables.add(var);
			
			dataMap.put(name, variables.get(variables.size() - 1));
		}
	}
	
	public static String [][] condense(String [][] lines){
		//first, count how many lines aren't null
		int counter = 0;
		while(lines[counter][0] != null) {
			counter++;
		}
		
		//now, make a new array that has only as many lines as needed
		String [][] condensed = new String[counter][lines[0].length];
		for(int i = 0; i < counter; i++) {
			condensed[i] = lines[i].clone();
		}
		
		return condensed;
	}

	public static int getInstructionCode(String ins) {
		ins = ins.toLowerCase();
		switch(ins) {
		case "mov" : return 0;
		case "add" : return 1;
		case "sub" : return 2;
		case "jmp" : return 3;
		case "jnq" : return 4;
		case "jeq" : return 5;
		case "cmp" : return 6;
		case "syscall" : return 7;


		//default case is if the passed string isn't a valid instruction.
		default: 
			//check if the line is a label
			if(!ins.startsWith(";") && ins.endsWith(":")) return 8;

			//garbo input
			else return -1;
		}
	}

	//checks to see what section of the file we're currently in during file parsing
	public static int checkSection(String [] line) {
		if(line.length < 2)
			return -1;
		for(String s : line) s = s.toLowerCase();

		String s = line[1];
		if(line[0].equals("section")) {
			switch(s) {
			case ".text" : return 0;
			case ".bss"  : return 1;
			case ".data" : return 2;

			default :
				return -1;
			}
		}
		return -1;
	}

	//this method isn't really nessesary but it helps slightly with organization
	public static int countLines(File f) throws FileNotFoundException {
		Scanner sc = new Scanner(f);
		int counter = 0;
		while(sc.hasNextLine()) {
			String s = sc.nextLine();
			s = s.trim();
			
			counter++;
		}
		sc.close();
		return counter;
	}



	public static void copy(byte [] a, byte [] b){
		byte [] c = new byte[b.length];
		a = c;
		//System.out.println("Source data: " + Arrays.toString(b));
		for(int i = 0; i < b.length; i++) {
			c[i] = b[i];
		}
		//fill the rest of the indicies with zero's
		for(int i = b.length; i < a.length; i++) {
			a[i] = 0;
		}
		
		
		for(byte [] d : registers)
			System.out.println(Arrays.toString(d));
		for(byte [] d : variables)
			System.out.println(Arrays.toString(d));
		System.out.println(); 
	}

	public static byte[] getByteRepresentation(String var) {
		int integerValue = 0;
		//if the var is a char
		if(var.startsWith("'")) {
			integerValue = (int) var.charAt(1);
		}else if(checkIsNumber(var)) {
			integerValue = Integer.parseInt(var);
		}else if(dataMap.containsKey(var)){			
			return dataMap.get(var);
		}else {
			System.out.println("WARNING! Variable not found?");
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(8); 
		buffer.putInt(integerValue); 
		return buffer.array();
	}
	
	public static boolean checkIsNumber(String var) {
		try {  
		    Integer.parseInt(var);  
		    return true;
		  } catch(NumberFormatException e){  
		    return false;  
		  }  
	}
	
	//defining the instruction functionalities
	
	static Instruction mov = (String [] params) -> {
		byte [] param1 = getByteRepresentation(params[1]);
		byte [] param2 = getByteRepresentation(params[2]);
		
		//if we're changing param1 directly
		if(!params[1].contains("[")) {

			//if we're accessing param2 directly
			if(!params[2].contains("[")) {
				//copy(dataMap.get(params[1]), dataMap.get(params[2]));
				copy(param1, param2);

				//if we're accessing the data param2 points 2
			}else {


			}
			//if we're changing the data param1 points to
		}else {

		}
	};

	static Instruction add = (String [] params) -> {
		int var1 = new BigInteger(dataMap.get(params[1])).intValue();
		int var2 = new BigInteger(dataMap.get(params[2])).intValue(); 
		int ans = var1 + var2;
		byte [] result = ByteBuffer.allocate(8).putInt(ans).array();
		dataMap.put(params[1], result);
	};

	static Instruction sub = (String [] params) -> {
		int var1 = new BigInteger(dataMap.get(params[1])).intValue();
		int var2 = new BigInteger(dataMap.get(params[2])).intValue(); 
		int ans = var1 - var2;
		byte [] result = ByteBuffer.allocate(8).putInt(ans).array();
		dataMap.put(params[1], result);
	};

	static Instruction jmp = (String [] params) -> {
		ip = Integer.parseInt(params[1]) - 1;
	};

	static Instruction jnq = (String [] params) -> {
		if(flag != 0){
			ip = Integer.parseInt(params[1]) - 1;
		}
	};

	static Instruction jeq = (String [] params) -> {
		if(flag == 0){
			ip = Integer.parseInt(params[1]) - 1;
		}
	};

	static Instruction cmp = (String [] params) -> {
		flag = Integer.parseInt(params[1]) - Integer.parseInt(params[2]);
	};

	static Instruction syscall = (String [] params) -> {
		int type = getVal(registers[0]);
		int size = getVal(registers[2]);
		
		//read in
		if(type == 0) {
			Scanner sc = new Scanner(System.in);
			String input = sc.next();
			mov.execute(new String[] {"mov", "r2", input});
			
			sc.close();
			
		//print out
		}else if(type == 1) {
			String output = new String(registers[1]);
			
			for(int i = 0; i < Math.min(size, output.length()); i++) {
				System.out.print(output.charAt(i));
			}
			System.out.println();
		}
	};
	
	public static int getVal(byte [] b) {
		return ByteBuffer.wrap(b).getInt();
	}
	
}