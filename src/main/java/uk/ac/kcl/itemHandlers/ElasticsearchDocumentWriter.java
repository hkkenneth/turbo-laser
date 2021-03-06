package uk.ac.kcl.itemHandlers;


import org.apache.poi.ss.formula.functions.T;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.shield.ShieldPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.support.transaction.TransactionAwareProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import uk.ac.kcl.exception.TurboLaserException;
import uk.ac.kcl.model.Document;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;


/**
 * Created by rich on 20/04/16.
 */
@Service("esDocumentWriter")
@Profile("elasticsearch")
public class ElasticsearchDocumentWriter implements ItemWriter<Document> {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchDocumentWriter.class);
    private long timeout;
    List<T> output = TransactionAwareProxyFactory.createTransactionalList();
    private ElasticsearchDocumentWriter() {}

    private String indexName;
    private String typeName;

    @Autowired
    Environment env;

    private Client client;
    @PostConstruct
    public void init() throws UnknownHostException {
        Settings settings;

        if(env.getProperty("elasticsearch.shield.enabled").equalsIgnoreCase("true")){
            settings =Settings.settingsBuilder()
                    .put("cluster.name", env.getProperty("elasticsearch.cluster.name"))
                    .put("shield.user", env.getProperty("elasticsearch.shield.user"))
                    .put("shield.ssl.keystore.path", env.getProperty("elasticsearch.shield.ssl.keystore.path"))
                    .put("shield.ssl.keystore.password", env.getProperty("elasticsearch.shield.ssl.keystore.password"))
                    //.put("shield.ssl.truststore.path", env.getProperty("elasticsearch.shield.ssl.truststore.path"))
                    //.put("shield.ssl.truststore.password", env.getProperty("elasticsearch.shield.ssl.truststore.password"))
                    .put("shield.transport.ssl", env.getProperty("elasticsearch.shield.transport.ssl"))
                    .build();
            client = TransportClient.builder()
                    .addPlugin(ShieldPlugin.class)
                    .settings(settings)
                    .build()
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(
                            env.getProperty("elasticsearch.cluster.host")),
                            Integer.valueOf(env.getProperty("elasticsearch.cluster.port"))));
        }else {
            settings =Settings.settingsBuilder()
                    .put("cluster.name", env.getProperty("elasticsearch.cluster.name")).build();
            client = TransportClient.builder()
                    .settings(settings)
                    .build()
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(
                            env.getProperty("elasticsearch.cluster.host")),
                            Integer.valueOf(env.getProperty("elasticsearch.cluster.port"))));
        }

        timeout = Long.valueOf(env.getProperty("elasticsearch.response.timeout"));
        indexName = env.getProperty("elasticsearch.index.name");
        typeName = env.getProperty("elasticsearch.type");
    }
    @PreDestroy
    public void destroy(){
        client.close();
    }

    @Override
    public final void write(List<? extends Document> documents) throws Exception {
        BulkRequestBuilder bulkRequest = client.prepareBulk();

        for (Document doc : documents) {
            XContentParser parser = null;
            parser = XContentFactory.xContent(XContentType.JSON)
                    .createParser(doc.getOutputData().getBytes());
            parser.close();
            XContentBuilder builder = jsonBuilder().copyCurrentStructure(parser);


            IndexRequestBuilder request = client.prepareIndex(
                    indexName,
                    typeName).setSource(
                    builder);
            request.setId(doc.getPrimaryKeyFieldValue());
            bulkRequest.add(request);
        }
        //check that no nonessential processes failed
        if(documents.size()!=0) {
            BulkResponse response;
            response = bulkRequest.execute().actionGet(timeout);
            getResponses(response);
        }
    }

    private void getResponses(BulkResponse response) {
        if (response.hasFailures()) {

            for (int i=0;i<response.getItems().length;i++){
                //in bulk processing, retry all docs one by one. if one fails, log it. If the entire chunk fails,
                // raise an exception towards skip limit
                if(response.getItems().length == 1){
                    LOG.warn("failed to index document: " + response.getItems()[i].getId() + " failure is: \n"
                            + response.getItems()[i].getFailureMessage());
                }else {
                    LOG.error("failed to index document: " + response.getItems()[i].getFailureMessage());
                    throw new ElasticsearchException("Bulk indexing request failed");
                }
            }
        }
        LOG.info("{} documents indexed into ElasticSearch in {} ms", response.getItems().length,
                response.getTookInMillis());
    }

}
