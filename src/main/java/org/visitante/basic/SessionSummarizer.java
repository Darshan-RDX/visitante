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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.visitante.basic;

import java.io.IOException;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Reducer.Context;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.chombo.util.TextLong;
import org.chombo.util.Tuple;
import org.chombo.util.Utility;
import org.visitante.basic.SessionExtractor.SessionIdGroupComprator;
import org.visitante.basic.SessionExtractor.SessionIdPartitioner;

public class SessionSummarizer  extends Configured implements Tool {

	@Override
	public int run(String[] args) throws Exception {
        Job job = new Job(getConf());
        String jobName = "web log session summarizer  MR";
        job.setJobName(jobName);
        
        job.setJarByClass(SessionSummarizer.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        Utility.setConfiguration(job.getConfiguration(), "visitante");
        
        job.setMapperClass(SessionExtractor.SessionMapper.class);
        job.setReducerClass(SessionExtractor.SessionReducer.class);

        job.setMapOutputKeyClass(TextLong.class);
        job.setMapOutputValueClass(Tuple.class);

        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        job.setGroupingComparatorClass(SessionIdGroupComprator.class);
        job.setPartitionerClass(SessionIdPartitioner.class);

        job.setNumReduceTasks(job.getConfiguration().getInt("num.reducer", 1));
        
        int status =  job.waitForCompletion(true) ? 0 : 1;
        return status;
	}
	
	public static class SessionReducer extends Reducer<TextLong, Tuple, NullWritable, Text> {
		private Text outVal = new Text();
		private String fieldDelim;
		private String sessionID;
		private String userID;
		private String lastUrl;
		private long timeSpent;
		private int numPages;
		
		protected void setup(Context context) throws IOException, InterruptedException {
        	fieldDelim = context.getConfiguration().get("field.delim.out", "[]");
       }
		
    	protected void reduce(TextLong key, Iterable<Tuple> values, Context context)
        	throws IOException, InterruptedException {
    		//TODO
    		
    	}
    }	

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new SessionSummarizer(), args);
        System.exit(exitCode);
    }
	
}
