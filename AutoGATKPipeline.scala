import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk.CommandLineGATK
import org.broadinstitute.sting.queue.util.QScriptUtils
import org.broadinstitute.sting.queue.extensions.gatk.RealignerTargetCreator
import org.broadinstitute.sting.queue.extensions.gatk.IndelRealigner
import org.broadinstitute.sting.queue.extensions.gatk.BaseRecalibrator
import org.broadinstitute.sting.queue.extensions.gatk.PrintReads
import org.broadinstitute.sting.queue.extensions.gatk.ReduceReads
import org.broadinstitute.sting.queue.extensions.gatk.HaplotypeCaller

/**
 * @author G. Tony Wang
 * @Date 11/25/2013
 * @This is my first attempt in chaining the multiple steps in GenomeAnalysisTK by using Queue 
 * from the BroadInstitute
 * @Java version: 1.7.45
 * @Platform: Mac OSX 10.6.8
 * 
 */


class AutoGATKPipeline extends QScript
{
  qscript =>
    
  @Input(doc = "Bam files to sort", shortName="I", required = true)
  var inputs: Seq[File] = Nil
  
  @Input(doc = "The reference file.", shortName="R", required = true)
  var referenceFile: File = _
  
  @Argument(doc = "known sites of dbsnps and indels", shortName = "knownSites", required = true)
  var knownSitesFiles: List[File] = Nil
  
  @Argument(doc = "chromosomal intervals", shortName = "L", required = true)
  var intervalFiles: List[File] = Nil
  
  
  trait CommonArguments extends CommandLineGATK
  {
    this.reference_sequence = qscript.referenceFile
  }
  
  def script()
  {
   
    if (inputs.size > 1)
    {
      for (bamFile <- inputs)
      {
        val singleRealignTarget = new RealignerTargetCreator with CommonArguments;
//        singleRealignTarget.I = Seq(bamFile)
        singleRealignTarget.input_file :+= bamFile
        singleRealignTarget.memoryLimit = 6
        singleRealignTarget.out = swapExt(bamFile, "bam", "intervals")
        add(singleRealignTarget)
        
        val singleIndelRealign = new IndelRealigner with CommonArguments;
        singleIndelRealign.input_file :+= bamFile
        singleIndelRealign.targetIntervals = singleRealignTarget.out
        singleIndelRealign.out = swapExt(bamFile, "bam", "realigned.bam")
        singleIndelRealign.memoryLimit = 6
        add(singleIndelRealign)
        
        val baseRecal = new BaseRecalibrator with CommonArguments
        baseRecal.input_file :+= singleIndelRealign.out
        baseRecal.knownSites = qscript.knownSitesFiles
        baseRecal.out = swapExt(singleIndelRealign.out, "bam", "recal.grp")
        baseRecal.memoryLimit = 6
        add(baseRecal)
        
        val printReads = new PrintReads with CommonArguments
        printReads.input_file :+= singleIndelRealign.out
        printReads.BQSR = baseRecal.out
        printReads.out = swapExt(baseRecal.out, "grp", "bam")
        printReads.memoryLimit = 6
        add(printReads)

        val reduceReads = new ReduceReads with CommonArguments
        reduceReads.input_file :+= printReads.out
        reduceReads.out = swapExt(printReads.out, "bam", "compressed.bam")
        reduceReads.memoryLimit = 6
        add(reduceReads)       
        
        val haploCaller = new HaplotypeCaller with CommonArguments
       
        haploCaller.input_file :+= reduceReads.out
        haploCaller.intervals = qscript.intervalFiles
        haploCaller.log_to_file = new File("HaploCaller-scala-log.log")
        haploCaller.out = swapExt(reduceReads.out, "bam", "vcf")
        haploCaller.memoryLimit = 6
        add(haploCaller)        
      }
     }
  }
  
  
  
  
}
