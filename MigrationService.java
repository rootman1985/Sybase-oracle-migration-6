package fr.migration;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.io.*;
import java.util.stream.Collectors;

public final class MigrationService {
    private final Config cfg;
    public MigrationService(Config cfg) { this.cfg = cfg; }
    private static String seconds(long nanos) { return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000_000.0); }
    private static String duration(long start) {
        Duration d = Duration.ofNanos(System.nanoTime()-start);
        long ms=d.toMillis();
        return String.format(Locale.ROOT,"%02d:%02d:%02d.%03d",ms/3600000,(ms/60000)%60,(ms/1000)%60,ms%1000);
    }
    private static String q(String s) { if(!s.matches("[A-Za-z_][A-Za-z0-9_$#]*")) throw new IllegalArgumentException("Invalid identifier: "+s); return s; }
    private static String csv(Object... values) {return Arrays.stream(values).map(v->"\""+String.valueOf(v).replace("\"","\"\"")+"\"").collect(Collectors.joining(","));}
    public void check(List<TableConfig> tables) {
        System.out.println("CHECK: "+tables.size()+" table(s) defined in the 9-column mapping CSV.");
        for(TableConfig t:tables) {
            System.out.printf("CHECK %s -> %s (%d mapped columns)%n",t.sourceQualifiedName(),t.targetQualifiedName(),t.columns().size());
            for(ColumnMapping m:t.columns()) {
                System.out.printf("  %s [%s] -> %s [%s]%n",m.sourceColumn(),m.sourceType(),m.targetColumn(),m.targetType());
            }
        }
        System.out.println("Note: CHECK validates mapping structure only, not live database columns.");
    }
    public void migrate(List<TableConfig> tables) throws Exception {
        Files.createDirectories(Path.of("logs"));
        long globalStart=System.nanoTime(), total=0;
        int success=0, failed=0;
        Path log=Path.of("logs","migration-tables-summary.csv");
        try(BufferedWriter out=Files.newBufferedWriter(log,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            Connection source=Db.openSybase(cfg); Connection target=Db.openOracle(cfg)) {
            if(Files.size(log)==0){out.write("source,target,rows_read,rows_committed,read_seconds,insert_seconds,commit_seconds,duration,status,error\n");out.flush();}
            target.setAutoCommit(false);
            for(TableConfig t:tables) {
                long started=System.nanoTime(), committed=0, readRows=0;
                long readNanos=0, insertNanos=0, commitNanos=0;
                String status="SUCCESS",error="";
                try {
                    source.setCatalog(t.sourceCatalog());
                    String columns=t.columns().stream().map(m->q(m.sourceColumn())).collect(Collectors.joining(","));
                    String targets=t.columns().stream().map(m->q(m.targetColumn())).collect(Collectors.joining(","));
                    String marks=String.join(",",Collections.nCopies(t.columns().size(),"?"));
                    String select="SELECT "+columns+" FROM "+q(t.sourceSchema())+"."+q(t.sourceTable());
                    String insert="INSERT INTO "+q(t.targetSchema())+"."+q(t.targetTable())+" ("+targets+") VALUES ("+marks+")";
                    System.out.println("START "+t.sourceQualifiedName()+" -> "+t.targetQualifiedName());
                    try(PreparedStatement reader=source.prepareStatement(select,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
                        PreparedStatement writer=target.prepareStatement(insert)) {
                        reader.setFetchSize(Math.max(1,cfg.batchSize()));
                        int pending=0;
                        long nextProgressAt=10000;
                        try(ResultSet rs=reader.executeQuery()) {
                            while(true) {
                                long nextStart=System.nanoTime();
                                boolean hasRow=rs.next();
                                readNanos += System.nanoTime()-nextStart;
                                if(!hasRow) break;
                                readRows++;
                                for(int i=0;i<t.columns().size();i++) ValueBinder.bind(rs,writer,i+1,t.columns().get(i));
                                writer.addBatch();pending++;
                                if(pending>=Math.max(1,cfg.oracleJdbcBatchSize())) {
                                    long insertStart=System.nanoTime();
                                    writer.executeBatch();
                                    insertNanos += System.nanoTime()-insertStart;
                                    long commitStart=System.nanoTime();
                                    target.commit();
                                    commitNanos += System.nanoTime()-commitStart;
                                    committed+=pending;pending=0;
                                    if (committed >= nextProgressAt) {
                                        System.out.printf("PROGRESS %s | read=%d | committed=%d | elapsed=%s | rs.next=%ss | insertBatch=%ss | commit=%ss%n",
                                                t.sourceTable(),readRows,committed,duration(started),seconds(readNanos),seconds(insertNanos),seconds(commitNanos));
                                        nextProgressAt = ((committed / 10000) + 1) * 10000;
                                    }
                                }
                            }
                        }
                        if(pending>0){
                            long insertStart=System.nanoTime();writer.executeBatch();insertNanos += System.nanoTime()-insertStart;
                            long commitStart=System.nanoTime();target.commit();commitNanos += System.nanoTime()-commitStart;
                            committed+=pending;
                        }
                    }
                    success++;total+=committed;
                    System.out.printf("TABLE DONE %s | read=%d | committed=%d | readNext=%ss | insert=%ss | commit=%ss | duration=%s%n",t.sourceTable(),readRows,committed,seconds(readNanos),seconds(insertNanos),seconds(commitNanos),duration(started));
                } catch(Exception e) {
                    failed++;status="FAILED";error=e.toString();
                    try{target.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
                    System.err.printf("TABLE FAILED %s | read=%d | committed=%d | duration=%s | %s%n",t.sourceTable(),readRows,committed,duration(started),e);
                } finally {
                    out.write(csv(t.sourceQualifiedName(),t.targetQualifiedName(),readRows,committed,seconds(readNanos),seconds(insertNanos),seconds(commitNanos),duration(started),status,error)+"\n");out.flush();
                }
            }
        }
        String summary=String.format(Locale.ROOT,"RUN SUMMARY tables=%d success=%d failed=%d rows_committed_in_successful_tables=%d duration=%s",tables.size(),success,failed,total,duration(globalStart));
        System.out.println(summary);
        Files.writeString(Path.of("logs","migration-run-summary.txt"),summary+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        if(failed>0)throw new IllegalStateException(failed+" table(s) failed. See logs/migration-tables-summary.csv");
    }
}
