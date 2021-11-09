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


interface Instruction{
	void execute(String [] params);
}

public class Project2 {
	static int debug = 0;

	//global variables
	static HashMap <String, byte[]> regMap; //used to convieniently address different parts of memory.
	static HashMap <String , Integer> varMap;

	static HashMap <String, Instruction> instructionMap; //contains the instructions of the code (.text section)

	static HashMap <String, Integer> functionMap; //map for functions
	static HashMap <String, Integer> labelMap; //map for labels

	static byte [][] registers = new byte[6][]; //each register can hold 4 bytes. (5 general purpose + 1 return address register)
	static Stack<byte [][]> registerSaver = new Stack<byte [][]>(); //stores the registers when a function call occurs
	
	static ArrayList<byte []> variables; //created when .data section is parsed
	
	static int ip = 0; 	//instruction pointer
	static int fip = Integer.MAX_VALUE - 1;	//function instruction pointer
	static Stack<Integer> pfip = new Stack<Integer>(); //previous function instruction pointer stack. This was added so that a function could call another function (recursion)
	static int sp = 0;	//stack pointer
	
	static int flag = 0;
	
	
	static byte [][] stack = new byte[2048][4];
	
	static String [][] functionInstructions = null;
	
	static Scanner sc;
	public static void main(String [] args) {
		//initialize a bunch of stuff
		sc = new Scanner(System.in);
		varMap = new HashMap<String, Integer>();
		labelMap = new HashMap<String, Integer>();
		functionMap = new HashMap<String, Integer>();
		for(int i = 0; i < 6; i++)
			registers[i] = new byte[4];
		
		//use a map for easy calling of the instructions
		instructionMap = new HashMap<String, Instruction>();		
		setupInstructionMap(instructionMap);

		//simmilarly, we use a map for addressing the memory (registers and variables made in .data).
		regMap = new HashMap<String, byte[]>();
		setupRegMap(regMap);

		//*******************************************************NOTE TO GRADER:************************************************************************
		//Change this line to point to the test file. Sorry if you wanted it done some other way.
		String dir = "C:\\...\\test1.txt";
		
		dir = "C:\\Users\\suprm\\git\\AssemblyEmulator\\AssemblyEmulator\\test files\\test3.txt"; //Personal testing
		
		File f = new File(dir);

		String [][] instructions = null;
		 

		try {
			parseData(f);
			instructions = parseInstructions(f);
			functionInstructions = parseFunctionInstructions(f);

			setupFunctionMap(functionInstructions);
			setupLabelMap(instructions);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}


		System.out.println("\n--------------------\nrunning program\n--------------------\n");
		runProgram(instructions, null, instructionMap);
		sc.close();
	}

	public static void setupLabelMap(String [][] instructions) {
		for(int i = 0; i < instructions.length; i++) {
			if(getInstructionCode(instructions[i][0]) == 8) { //if the line is a label
				labelMap.put(instructions[i][0], i);
			}
		}
	}

	public static void setupFunctionMap(String [][] functionInstructions) {
		for(int i = 0; i < functionInstructions.length; i++) {
			if(getInstructionCode(functionInstructions[i][0]) == 8) { //if the line is a label
				functionMap.put(functionInstructions[i][0], i);
			}
		}
	}

	
	public static void runProgram(String [][] instructions, byte [][] data, HashMap <String, Instruction> instructionMap) {
		for(ip = 0; ip < instructions.length; ip++) {

			//stuff to print the instruction we're at, and what the register values are at each iteration
			if(debug > 0) { 
				System.out.print(ip + "\t");
				for(String s : instructions[ip])
					System.out.print(s + " ");
				System.out.println();

				if(debug > 1) {
					System.out.print("\tregisters:");
					int i = 1;
					for(byte [] d : registers) {
						System.out.print("\t" + i + ": " + Arrays.toString(d) + ", ");
						i++;
					}
					System.out.println();
				}
			}

			//do the instruction
			Instruction p = instructionMap.get(instructions[ip][0]);
			if(p != null)
				p.execute(instructions[ip]);
		}
	}
	
	public static void runFunction(byte [][] data, HashMap <String, Instruction> instructionMap) {
		while(fip < functionInstructions.length) {
			
			
			//stuff to print the instruction we're at, and what the register values are at each iteration
			if(debug > 0) { 
				System.out.print("\t" + fip + "\t");
				for(String s : functionInstructions[fip])
					System.out.print(s + " ");
				System.out.println();

				if(debug > 1) {
					System.out.print("\tregisters:");
					int i = 1;
					for(byte [] d : registers) {
						System.out.print("\t" + i + ": " + Arrays.toString(d) + ", ");
						i++;
					}
					System.out.println();
				}
			}

			//do the instruction
			Instruction p = instructionMap.get(functionInstructions[fip][0]);
			if(p != null)
				p.execute(functionInstructions[fip]);
			
			fip++;
		}
		fip = Integer.MAX_VALUE - 1;
	}

	public static void setupInstructionMap(HashMap<String, Instruction> map) {
		map.put("mov", mov);
		map.put("add", add);
		map.put("sub", sub);
		map.put("jmp", jmp);
		map.put("cmp", cmp);
		map.put("jne", jnq);
		map.put("jeq", jeq);
		map.put("call", call);
		map.put("ret", ret);
		map.put("syscall", syscall);
	}

	public static void setupRegMap(HashMap<String, byte[]> map) {
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
					if(debug > 1) System.out.println(Arrays.toString(lineArgs));
					counter++;
				}
				//output = output.concat(s + "\n");

			}
		}
		instructions = condense(instructions);
		if(debug > 0) {
			System.out.println("\nprinting instructions---------");
			for(int i = 0; i < instructions.length; i++) {
				System.out.println(Arrays.toString(instructions[i]));
			}
			System.out.println("done printing-----------------");
		}
		sc.close();

		return instructions;
	}

	//iterate through the first chunks of the line (this is all we'd ever care about. any more than 3, and its a comment or something)
	public static String [][] parseFunctionInstructions(File f) throws FileNotFoundException {
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

			//make sure we're in the .Func section
			if(checkSection(lineArgs) == 3)
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
					if(debug > 1) System.out.println(Arrays.toString(lineArgs));
					counter++;
				}
				//output = output.concat(s + "\n");

			}
		}
		instructions = condense(instructions);
		if(debug > 0) {
			System.out.println("\nprinting function instructions---------");
			for(int i = 0; i < instructions.length; i++) {
				System.out.println(Arrays.toString(instructions[i]));
			}
			System.out.println("done printing-----------------");
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



				//save only the first 4 args (label, size, text/data, possible extra newline at the end)
				String [] first4Args = new String[1024];
				for(int i = 0; i < Math.min(1024, lineArgs.length); i++) { 
					if(lineArgs[i].equals(";"))
						break;
					first4Args[i] = lineArgs[i];
				}

				//save this line's code
				data[counter] = first4Args;
				//System.out.println(Arrays.toString(lineArgs));
				counter++;
				//	}
				//output = output.concat(s + "\n");


			}
		}

		data = condense(data);
		if(debug > 0) {
			System.out.println("\nprinting data-----------------");
			for(int i = 0; i < data.length; i++) {
				System.out.println(Arrays.toString(data[i]));
			}
			System.out.println("done printing-----------------");
		}
		sc.close();

		storeData(data);

		return data;
	}

	public static void storeData(String [][] lines) {
		variables = new ArrayList<byte []>();

		for(int i = 0; i < lines.length; i++) {


			String [] line = lines[i];
			if(!line[0].equals("resb")) {
				String name = line[1]; //.substring(0, line[0].length() - 1); //use substring to cut off the ":"
				String length = line[0].toLowerCase();
				//String data = line[2];
				String message = "";
				int len = 0;
				switch(length) {
				case "db" : len = 1; break;
				case "dw" : len = 2; break;
				case "dd" : len = 4; break;
				default: if(debug > 0) System.out.println("Unknown length error : " + length); continue; 
				}

				boolean lenVar = false;
				boolean quotes = false;
				int startQuotes = 0;
				for(int j = 2; j < 1024; j++) {
					if(line[j] == null)
						break;
					if(line[j].startsWith("\"")) {
						quotes = true;
					}else if(line[j].matches("[l][e][n][(][a-zA-Z0-9]*[)]")) {
						lenVar = true;
					}

					if(lenVar) {
						byte [] var = new byte[len];
						String nameOfVar = line[j].substring(4, line[j].length() - 1);

						int lengthOfString = variables.get(varMap.get(nameOfVar)).length;
						ByteBuffer buffer = ByteBuffer.allocate(4); 
						buffer.putInt(lengthOfString); 
						var = buffer.array();
						if(debug > 1) System.out.println("Getting length of " + nameOfVar + " = " + lengthOfString);

						variables.add(var);
						//we will map variable names to arraylist indecies
						varMap.put(name, variables.size() - 1);	
						//System.out.println(variables + " " + variables.size());
						if(debug > 1) System.out.println("added : " + name + " at " + (variables.size() - 1) + " with value : " + getVal(variables.get((variables.size() - 1))));
					}
					if(quotes) {
						if(startQuotes == 0)
							startQuotes = j;
						message += line[j].replace('\"', ' ') + " ";

						if(line[j].endsWith("\"") && j != startQuotes) {
							quotes = false;
						}
					}
					if(!quotes && line[j].contains(";"))
						break;
				}
				message = message.trim();




				//first, we need to convert the string length to an int
				//				switch(length) {
				//				case "
				//				}


				if(!lenVar) {
					byte [] var = new byte[len];
					var = message.getBytes();
					if(debug > 1) System.out.println("message = " + toString(var));

					variables.add(var);
					//we will map variable names to arraylist indecies
					varMap.put(name, variables.size() - 1);	
					if(debug > 1) System.out.println("added : " + name + " at " + (variables.size() - 1) + " with value : " + toString(variables.get((variables.size() - 1))));

				}

			}else {
				String name = line[1]; //.substring(0, line[0].length() - 1); //use substring to cut off the ":"
				int length = Integer.parseInt(line[2].toLowerCase());
				//4 bytes is the minimum length, since any less would result in a size mismatch error in real assembly
				//(you can't move a 4 byte register into a 2 byte variable, or vice versa.)
				//this is done for simplicity
				length = Math.max(4, length); 
				byte [] var = new byte[length];

				variables.add(var);
				//we will map variable names to arraylist indecies
				varMap.put(name, variables.size() - 1);	

				if(debug > 1) System.out.println("added : " + name + " at " + (variables.size() - 1) + " with value : " + Arrays.toString(variables.get((variables.size() - 1))));

			}
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
		case "ret" : return 8;
		case "call" : return 9;
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

		String s = line[1].toLowerCase();
		//System.out.println(s);
		if(line[0].toLowerCase().equals("section")) {
			switch(s) {
			case ".text" : return 0;
			case ".bss"  : return 1;
			case ".data" : return 2;
			case ".func" : return 3;

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
		//		byte [] c = new byte[b.length];
		//		a = c;
		if(debug > 2) System.out.println("Source data: " + Arrays.toString(a) + ", " + Arrays.toString(b));


		//fill the rest of the indicies with zero's
		for(int i = 0; i < a.length; i++) {
			a[i] = 0;
		}

		//copy the data
		int lenDif = Math.abs(b.length - a.length);
		int c = lenDif;
		for(int i = 0; i < Math.min(b.length, a.length); i++) {
			if(a.length > b.length) {
				a[i + c] = b[i];
			}else {
				a[i] = b[i + c];
			}
		}


		if(debug > 2) System.out.println("After copy data: " + Arrays.toString(a) + ", " + Arrays.toString(b));

		//		for(byte [] d : registers)
		//			System.out.println(Arrays.toString(d));
		//		for(byte [] d : variables)
		//			System.out.println(Arrays.toString(d));
		//		System.out.println(); 
	}

	public static byte[] getByteRepresentation(String var) {
		//System.out.println(var);
		int integerValue = 0;

		if(var.startsWith("'")) { //if the var is a char
			integerValue = (int) var.charAt(1);
		}else if(checkIsNumber(var)) { //if var is a number
			integerValue = Integer.parseInt(var);
		}else if(regMap.containsKey(var)){//register is being accessed
			return regMap.get(var);
		}else if(regMap.containsKey(var.substring(1, var.length() - 1))){ //if a register is being dereferenced
			return regMap.get(var.substring(1, var.length() - 1));
		}else if(varMap.containsKey(var)){ //if var is a variable (that's made in .data section)
			integerValue = varMap.get(var);

		}else if(varMap.containsKey(var.substring(1, var.length() - 1))) { //in case the var is being dereferenced
			integerValue = varMap.get(var.substring(1, var.length() - 1));
			//System.out.println("int value = " + integerValue); 
		}else if(var.contains("[sp+")) { //if the variable is referencing the stack pointer
			int offset = Integer.parseInt(var.substring(4,5));			
			return stack[offset + sp];
		}else {
			System.out.println("WARNING! Variable not found? " + var);
		}

		return itob(integerValue);
	}
	
	static byte [] itob(int i) {
		ByteBuffer buffer = ByteBuffer.allocate(4); 
		buffer.putInt(i); 		
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
		//		byte [] param1 = null;
		//		byte [] param2 = null;

		//print the vars
//		for(byte b : param1) {
//			System.out.print(b + " ");
//		}
//		System.out.println();
//		for(byte b : param2) {
//			System.out.print(b + " ");
//		}

		//System.out.println();
		//if we're changing param1 directly
		if(!params[1].contains("[")) {

			//if we're accessing param2 directly
			if(!params[2].contains("[") || params[2].contains("[sp+")) {
				//copy(regMap.get(params[1]), regMap.get(params[2]));				
				copy(param1, param2);
				//				for(byte b : param1) {
				//					System.out.print(b + " ");
				//				}
				//				System.out.println();
				//				for(byte b : param2) {
				//					System.out.print(b + " ");
				//				}
				//				System.out.println();
				//we're dereferencing param2
			}else {

				byte [] deref2 = variables.get(getVal(param2));
				//				System.out.println("deref of :" + getVal(param2));
				//				for(byte b : deref2) {
				//					System.out.print(b + " ");
				//				}
				//				System.out.println();
				copy(param1, deref2);
				printAll();
			}
			//if we're changing the data param1 points to
		}else {
			if(!params[2].contains("[")) {
				byte [] deref1 = variables.get(getVal(param1));
				if(debug > 2) {
					System.out.println("deref of var at index " + getVal(param1) + " :");
					for(byte b : deref1) {
						System.out.print(b + " ");
					}
					System.out.println();
				}
				copy(deref1, param2);
				printAll();
			}
		}
	};

	public static int stoi(String s) {
		return (regMap.get(s) != null) ? new BigInteger(regMap.get(s)).intValue() : Integer.parseInt(s);
	}

	static Instruction add = (String [] params) -> {
		int var1 = stoi(params[1]);
		int var2 = stoi(params[2]);
		if(debug > 1) System.out.println("Add : " + var1 + " " + var2);
		int ans = var1 + var2;
		byte [] result = ByteBuffer.allocate(4).putInt(ans).array();
		if(debug > 1) System.out.println("ADD RESULT = " + getVal(result));
		regMap.put(params[1], result);
	};

	static Instruction sub = (String [] params) -> {
		if(debug > 1) System.out.println(params[1] + " " + params[2]);
		int var1 = stoi(params[1]);
		int var2 = stoi(params[2]);
		int ans = var1 - var2;
		byte [] result = ByteBuffer.allocate(4).putInt(ans).array();
		regMap.put(params[1], result);
	};

	//needs to save the current state of the registers, push the registers onto the stack, then jmp to the function
	static Instruction call = (String [] params) -> {
		registers[5] = itob(ip); //set the return adress register
		
		pfip.push(fip); //add the current function instruction to stack so that we can return to it later
		
		
		sp += 5; //this implementation of the stack means we'll never use indecies 0-4, but that doesn't really matter.
		//push the registers onto the stack so that they can be used by the function		
		for(int i = sp; i < 5 + sp; i++) {
			stack[i] = registers[i - sp].clone();
		}
		
		if(debug > 1) {
			System.out.print("STACK : ");
			for(int i = sp; i < sp + 5; i++) {
				System.out.print("i=" + i + ": " + Arrays.toString(stack[i]) + " ");
			}
			System.out.println();
		}
		registerSaver.push(deepCopy(registers)); //saves the registers
		
		fip = functionMap.get(params[1].concat(":")); //sets the function instruction pointer to the correct location
		if(debug > 1) System.out.println("running function");
		runFunction(null, instructionMap);
		if(debug > 1) System.out.println("done running function");
		
		//removes the registers from the stack
		for(int i = sp + 4; i >= sp; i--) {
			stack[i] = new byte[4];			
		}
		
		byte [][] oldRegisterValues = registerSaver.pop();
		resetRegisters(oldRegisterValues);
		
		sp -= 5;
	};
	static void resetRegisters(byte [][] old) {
		for(int i = 0; i < old.length; i++) {
			for(int j = 0; j < old[0].length; j++) {
				registers[i][j] = old[i][j];
			}
		}
	}
	
	static Instruction ret = (String [] params) -> {
		fip = pfip.pop();		
	};

	static Instruction jmp = (String [] params) -> {		
		ip = (labelMap.containsKey(params[1].concat(":"))) ? labelMap.get(params[1].concat(":")) : Integer.parseInt(params[1]) - 1;		
	};

	static Instruction jnq = (String [] params) -> {
		if(flag != 0){
			ip = (labelMap.containsKey(params[1].concat(":"))) ? labelMap.get(params[1].concat(":")) : Integer.parseInt(params[1]) - 1;		
		}
	};

	static Instruction jeq = (String [] params) -> {
		if(flag == 0){
			if(debug > 1) System.out.println("\n\n\n\n\n\nJUMP " + params[1] + ", " + params[2]);
			ip = (labelMap.containsKey(params[1].concat(":"))) ? labelMap.get(params[1].concat(":")) : Integer.parseInt(params[1]) - 1;	

		}
	};

	static Instruction cmp = (String [] params) -> {
		if(debug > 1) System.out.println("\n\n\n\nCOMPARE " + params[1] + " " + params[2]);

		int var1 = stoi(params[1]);
		int var2 = stoi(params[2]);
		if(debug > 1) System.out.println("compare vals : " + var1 + " " + var2);

		int ans = var1 - var2;
		flag =  Integer.signum(ans);

		if(debug > 1) System.out.println("flag = " + flag);
	};

	static Instruction syscall = (String [] params) -> {
		printAll();
		int type = getVal(registers[0]);
		int size = getVal(registers[2]);

		//read in
		if(type == 0) {


			String input = sc.next();
			if(debug > 2) System.out.println("input = " + input + ", storage = " + getVal(registers[1]) + " val ? " + (int)input.charAt(0));
			byte []  param1 = getByteRepresentation("[r2]");
			byte []  param2 = new byte[Math.max(4, input.length())];
			int c = 0;
			if(input.length() < 4) {
				for(int i = 4 - input.length(); i < 4; i++) {
					int val = (int)input.charAt(c);
					param2[i] = (byte)val;
					c++;
				}
			}else {
				for(int i = 0; i < input.length(); i++) {
					int val = (int)input.charAt(i);
					param2[i] = (byte)val;
				}
			}
			if(debug > 2) System.out.println("\n\n\n\nto string param2 = " + toString(param2) + ", val = " + getVal(param2));
			byte [] deref1 = variables.get(getVal(param1));
			if(debug > 2) {
				System.out.println("deref of var at index " + getVal(param1) + " :");
				for(byte b : deref1) {
					System.out.print(b + " ");
				}
				System.out.println();
			}
			copy(deref1, param2);
			//mov.execute(new String[] {"mov", "[r2]", input});
			printAll();
			//System.out.println("\n\nto STRING " + toString(variables.get(3)));
			if(debug > 2) System.out.println("\n\n\nafter put : " + toString(variables.get(getVal(registers[1]))));


			//print out
		}else if(type == 1) {

			String output = new String(variables.get(getVal(registers[1])));
			output = output.trim();
			if(debug > 0) System.out.println("output = ? " + output + " size = " + size + ", len = " + output.length());
			for(int i = 0; i < Math.min(size, output.length()); i++) {
				if(output.charAt(i) == '\\' && output.charAt(i + 1) == 'n') { //if we're to print a newline
					System.out.println();
					i++;
				}else
					System.out.print(output.charAt(i));
			}
			if(debug > 0) System.out.println();
		}
	};

	public static int getVal(byte [] b) {
		return ByteBuffer.wrap(b).getInt();
	}

	public static void printAll() {
		if(debug > 2) {
			System.out.println("registers:");
			int i = 1;
			for(byte [] d : registers) {
				System.out.println(i + " : " + Arrays.toString(d));
				i++;
			}
			i = 0;

			System.out.println("variables:" + varMap);
			for(byte [] d : variables) {

				System.out.println(i + " : " + Arrays.toString(d) + " String representation: " + toString(d));
				i++;
			}
			System.out.println(); 
		}
	}
	public static String toString(byte [] d) {
		String s = "";
		for(byte b : d) {
			s += (char)b;
		}
		return s;
	}
	
	//clones a 2d byte array. this is used when saving register values before a function call
	public static byte[][] deepCopy(byte[][] array) {
	    int rows = array.length;
	   
	    byte [][] copy = new byte[rows][];
	    
	    for(int i = 0; i < rows; i++){
	    	copy[i] = (byte[]) array[i].clone();
	    }

	    return copy;
	}
}