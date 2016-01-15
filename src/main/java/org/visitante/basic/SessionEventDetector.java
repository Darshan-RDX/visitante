/*
 * visitante: Web analytic using Hadoop Map Reduce
 * Author: Pranab Ghosh
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, softwarSessionSummarizere
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */


package org.visitante.basic;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.chombo.util.SecondarySort;
import org.chombo.util.TextLong;
import org.chombo.util.Tuple;
import org.chombo.util.Utility;

public class SessionEventDetector  extends Configured implements Tool {

	@Override
	public int run(String[] args) throws Exception {
        Job job = new Job(getConf());
        String jobName = "web log user  event detector  MR";
        job.setJobName(jobName);
        
        job.setJarByClass(SessionEventDetector.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        Utility.setConfiguration(job.getConfiguration(), "visitante");
        
        job.setMapperClass(SessionEventDetector.EventMapper.class);
        job.setReducerClass(SessionEventDetector.EventReducer.class);

        job.setMapOutputKeyClass(Tuple.class);
        job.setMapOutputValueClass(Text.class);

        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        job.setGroupingComparatorClass(SecondarySort.TuplePairGroupComprator.class);
        job.setPartitionerClass(SecondarySort.TupleTextPartitioner.class);

        job.setNumReduceTasks(job.getConfiguration().getInt("ee.num.reducer", 1));
        int status =  job.waitForCompletion(true) ? 0 : 1;
        return status;
	}
	
	/**
	 * @author pranab
	 *
	 */
	public static class EventMapper extends Mapper<LongWritable, Text, Tuple, Text> {
		private String[] items;
		private Tuple outKey = new Tuple();
		private Text outVal = new Text();
        private String fieldDelimRegex;
        private Map<String, String> filedMetaData;
        private static final String itemDelim = ",";
        private static final String keyDelim = ":";
        private int cookieOrd;
        private   int dateOrd;
        private  int timeOrd;
        private SimpleDateFormat dateFormat;
        private Date date;
        private Long timeStamp;
        private String sessionIDName;
        private String userIDName;
        private String cookie;
        private String sessionID;
        private String userID;
        private String[] cookieItems;
        private String cookieSeparator;
        
        protected void setup(Context context) throws IOException, InterruptedException {
        	fieldDelimRegex = context.getConfiguration().get("field.delim.regex", "\\s+");
        	String fieldMetaSt = context.getConfiguration().get("field.meta");
        	System.out.println("fieldMetaSt:" + fieldMetaSt);
        	
        	filedMetaData=Utility.deserializeMap(fieldMetaSt, itemDelim, keyDelim);
        	cookieOrd =new Integer(filedMetaData.get("cookie"));
            dateOrd =new Integer(filedMetaData.get("date"));
            timeOrd =new Integer(filedMetaData.get("time"));
            dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sessionIDName = context.getConfiguration().get("session.id.name");
            userIDName = context.getConfiguration().get("user.id.name");
            cookieSeparator = context.getConfiguration().get("cookie.separator", ";\\+");
       }
        
        @Override
        protected void map(LongWritable key, Text value, Context context)
            throws IOException, InterruptedException {
            items  =  value.toString().split(fieldDelimRegex);
            try {
				date = dateFormat.parse(items[dateOrd] + " " + items[timeOrd]);
				timeStamp = date.getTime();
				getSessionID();
				outKey.initialize();
				outKey.add(sessionID, timeStamp);
				
				outVal.set(value.toString());
   	   			context.write(outKey, outVal);
			} catch (ParseException ex) {
				throw new IOException("Failed to parse date time", ex);
			}
        }
        
        /**
         * 
         */
        private void  getSessionID() {
        	cookie = items[cookieOrd];
        	cookieItems = cookie.split(cookieSeparator);
        	for (String item :  cookieItems) {
        		if (item.startsWith(sessionIDName)) {
        			sessionID = item.split("=")[1];
        		}
        		if (item.startsWith(userIDName)) {
        			userID = item.split("=")[1];
        		}
        	}
        }
        
	}
	
	/**
	 * @author pranab
	 *
	 */
	public static class EventReducer extends Reducer<Tuple, Text, NullWritable, Text> {
		private Text outVal = new Text();
		private String fieldDelim;
		private int matchContextSize;
		private Map<String, Pattern> patterns = new HashMap<String, Pattern>();
		private List<String> records = new ArrayList<String>();
		
		/* (non-Javadoc)
		 * @see org.apache.hadoop.mapreduce.Reducer#setup(org.apache.hadoop.mapreduce.Reducer.Context)
		 */
		protected void setup(Context context) throws IOException, InterruptedException {
        	Configuration config = context.getConfiguration();
        	fieldDelim = config.get("field.delim.out", ",");
        	matchContextSize = config.getInt("match.context.size", 2);
        	
        	//patterns
        	String key = null;
        	String name = null;
        	String regex = null;
        	for (int i = 1;  ; ++i) {
        		key = "event.pattern." + i + ".name";
        		name = config.get(key);
        		if (null == name) {
        			break;
        		}
        		key = "event.pattern." + i + ".regex";
        		regex = config.get(key);
        		patterns.put(key, Pattern.compile(regex));
        	}
       }
		
    	/* (non-Javadoc)
    	 * @see org.apache.hadoop.mapreduce.Reducer#reduce(KEYIN, java.lang.Iterable, org.apache.hadoop.mapreduce.Reducer.Context)
    	 */
    	protected void reduce(Tuple key, Iterable<Text> values, Context context)
        	throws IOException, InterruptedException {
    		records.clear();
    		for (Text value : values) {
    			records.add(value.toString());
    		}
    		
    		//try matching all patterns
    		for (String patternName : patterns.keySet()) {
    			int i = 0;
    			//all records
    			boolean firstMatch = true;
    			for (String record : records) {
    				if (patterns.get(patternName).matcher(record).matches()) {
    					int beg = i - matchContextSize;
    					beg = beg < 0 ? 0 : beg;
    					int end =  i + matchContextSize;
    					end = end > records.size() -1 ? records.size() -1 : end;
    					
    					if (firstMatch) {
    						outVal.set("event: " + patternName);
    						context.write(NullWritable.get(),outVal);
        					firstMatch = false;
    					}
						
    					outVal.set(" ");
						context.write(NullWritable.get(),outVal);
						for (int j = beg;  j <= end; ++j) {
    						outVal.set(records.get(j));
    						context.write(NullWritable.get(),outVal);
						}
    					
    				}
    				++i;
    			}
    		}
    	}
    	
	}
	
    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new SessionEventDetector(), args);
        System.exit(exitCode);
    }
	
}
