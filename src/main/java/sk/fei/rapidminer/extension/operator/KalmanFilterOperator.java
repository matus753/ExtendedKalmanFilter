package sk.fei.rapidminer.extension.operator;

import java.util.List;
import java.util.logging.Level;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.Attributes;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.metadata.AttributeMetaData;
import com.rapidminer.operator.ports.metadata.ExampleSetMetaData;
import com.rapidminer.operator.ports.metadata.ExampleSetPassThroughRule;
import com.rapidminer.operator.ports.metadata.MetaData;
import com.rapidminer.operator.ports.metadata.SetRelation;
import com.rapidminer.operator.ports.metadata.SimplePrecondition;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.UndefinedParameterError;
import com.rapidminer.parameter.conditions.BooleanParameterCondition;
import com.rapidminer.tools.LogService;
import com.rapidminer.tools.Ontology;

public class KalmanFilterOperator extends Operator{
	
	public static final String PARAMETER_RANGE_OF_NOISE = "range of noise";
	public static final String PARAMETER_CONSTANT_A = "constant A";
	public static final String PARAMETER_CONSTANT_B = "constant B";
	public static final String PARAMETER_CONSTANT_C = "constant C";
	public static final String PARAMETER_START_VALUE_OF_P = "start value of P";
	public static final String PARAMETER_DEFINE_START_VALUE = "define x0";
	public static final String PARAMETER_START_VALUE = "x0";
	
	private InputPort exampleSetInput = getInputPorts().createPort("example set", ExampleSet.class);
	
	private OutputPort exampleSetOutput = getOutputPorts().createPort("example set");
	
	public KalmanFilterOperator(OperatorDescription description){
		super(description);
		
		exampleSetInput.addPrecondition(new SimplePrecondition( exampleSetInput, new MetaData(ExampleSet.class)));
		
		getTransformer().addPassThroughRule(exampleSetInput,exampleSetOutput);
		getTransformer().addRule(new ExampleSetPassThroughRule( exampleSetInput, exampleSetOutput, SetRelation.EQUAL){
			@Override
			public ExampleSetMetaData modifyExampleSet(ExampleSetMetaData metaData ) throws UndefinedParameterError{
				metaData.addAttribute(new AttributeMetaData("result", Ontology.REAL));
				metaData.addAttribute(new AttributeMetaData("statistic", Ontology.INTEGER));
				return metaData;
			}
		});

	}
	
	@Override
	public List<ParameterType> getParameterTypes(){
		List<ParameterType> types = super.getParameterTypes();
		
		types.add(new ParameterTypeBoolean(
				PARAMETER_DEFINE_START_VALUE,
				"If checked, a custom x0 can be enter.",
				false,
				false));
		
		ParameterType type = new ParameterTypeDouble(
				PARAMETER_START_VALUE,
				"This parameter defines custom start value x0.",
				Double.MIN_VALUE,
				Double.MAX_VALUE,
				true);
		
		type.registerDependencyCondition(
				new BooleanParameterCondition(
				this, PARAMETER_DEFINE_START_VALUE, true, true));

		types.add(type);
		
		types.add(new ParameterTypeDouble(
				PARAMETER_CONSTANT_A,
				"This parameter defines costant A in equation for xk.",
				Integer.MIN_VALUE,
				Integer.MAX_VALUE,
				0.8));
		
		types.add(new ParameterTypeDouble(
				PARAMETER_CONSTANT_B,
				"This parameter defines costant B in equation for xk.",
				Integer.MIN_VALUE,
				Integer.MAX_VALUE,
				0.2));
		
		types.add(new ParameterTypeDouble(
				PARAMETER_CONSTANT_C,
				"This parameter defines costant C in equation for zk.",
				Integer.MIN_VALUE,
				Integer.MAX_VALUE,
				1));
		
		types.add(new ParameterTypeDouble(
				PARAMETER_START_VALUE_OF_P,
				"This parameter defines start parameter p0 in equation for measurment.",
				Integer.MIN_VALUE,
				Integer.MAX_VALUE,
				1));
		
		types.add(new ParameterTypeDouble(
				PARAMETER_RANGE_OF_NOISE,
				"This parameter defines which text is logged to the console when this operator is executed.",
				Integer.MIN_VALUE,
				Integer.MAX_VALUE,
				true));

		return types;
	}
	
	@Override
	public void doWork() throws OperatorException{
		long startTime = System.currentTimeMillis();
		LogService.getRoot().log(Level.INFO,"Kalman filter start!");
		ExampleSet exampleSet = exampleSetInput.getData(ExampleSet.class);
		// get attributes from example set
		Attributes attributes = exampleSet.getAttributes();
				
		Double R = getParameterAsDouble(PARAMETER_RANGE_OF_NOISE);
		Double A = getParameterAsDouble(PARAMETER_CONSTANT_A);
		Double B = getParameterAsDouble(PARAMETER_CONSTANT_B);
		Double C = getParameterAsDouble(PARAMETER_CONSTANT_C);
		Double Pk = getParameterAsDouble(PARAMETER_START_VALUE_OF_P);
		Boolean choise = getParameterAsBoolean(PARAMETER_DEFINE_START_VALUE);
		Double xk,zk,x;
		//parameter for gain
		Double gk;
		Integer u = 1;
		
		if((A + B) != 1){
			throw new OperatorException("Sum of constant A and B have to be 1!");
		}
				
		if(choise){
			xk = getParameterAsDouble(PARAMETER_START_VALUE);
		} else {
			xk = exampleSet.getExample(0).getValue(attributes.getLabel());
		}
		x = xk;
		
		
	
		LogService.getRoot().log(Level.INFO,"x0 = " + xk);
		
		// create a new attribute
		String newName = "result";
		Attribute targetAttribute = AttributeFactory.createAttribute(newName, Ontology.REAL);

		targetAttribute.setTableIndex(attributes.size());
		exampleSet.getExampleTable().addAttribute(targetAttribute);
		attributes.addRegular(targetAttribute);
		
		newName = "statistic";
		Attribute statistic = AttributeFactory.createAttribute(newName, Ontology.INTEGER);

		statistic.setTableIndex(attributes.size());
		exampleSet.getExampleTable().addAttribute(statistic);
		attributes.addRegular(statistic);
		
		
		Attribute label = attributes.getLabel();
		
		Boolean realUp,predictUp;
		Double lastOneReal = xk, lastOnePredict = xk;
		
		//CALCULATE
		for(Example example:exampleSet){
			//predict
			xk = A*xk + B*example.getValue(label);
			Pk = A*Pk*A;
			
			x = A*x + B*example.getValue(label);
			zk = C*x;
			
			//update
			gk = Pk == 0 ? 1 : Pk*C/(C*Pk*C + R);
			xk = xk + (gk*(zk - C*xk));
			Pk = (1 - gk*C)*Pk;
			
			realUp = example.getValue(label) > lastOneReal;
			predictUp = xk > lastOnePredict;
			
			if(realUp == predictUp){
				example.setValue(statistic, 1);
			} else {
				example.setValue(statistic, 0);
			}
			
			lastOneReal = example.getValue(label);
			lastOnePredict = xk;
			example.setValue(targetAttribute, xk);
		}
		

		exampleSetOutput.deliver(exampleSet);
		long endTime = System.currentTimeMillis() - startTime;
		LogService.getRoot().log(Level.INFO,"Kalman filter ends! Duration: " + endTime + " ms");
	}

}
