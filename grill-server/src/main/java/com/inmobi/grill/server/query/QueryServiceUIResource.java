package com.inmobi.grill.server.query;

import com.google.common.base.Joiner;
import com.inmobi.grill.api.GrillConf;
import com.inmobi.grill.api.GrillException;
import com.inmobi.grill.api.GrillSessionHandle;
import com.inmobi.grill.api.query.*;
import com.inmobi.grill.server.GrillServices;
import com.inmobi.grill.server.api.driver.GrillResultSetMetadata;
import com.inmobi.grill.server.api.query.QueryExecutionService;
import com.inmobi.grill.server.session.SessionUIResource;
import org.apache.commons.io.IOExceptionWithCause;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import java.io.UnsupportedEncodingException;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.ArrayList;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by arun on 25/7/14.
 */
@Path("/queryuiapi")
public class QueryServiceUIResource{

    public static final Log LOG = LogFactory.getLog(QueryServiceUIResource.class);

    private QueryExecutionService queryServer;

    private void checkQuery(String query) {
        if (StringUtils.isBlank(query)) {
            throw new BadRequestException("Invalid query");
        }
    }

    private void checkSessionHandle(GrillSessionHandle sessionHandle) {
        if (sessionHandle == null) {
            throw new BadRequestException("Invalid session handle");
        }
    }

    public QueryServiceUIResource() {
        LOG.info("Query UI Service");
        queryServer = (QueryExecutionService) GrillServices.get().getService("query");
    }

    private QueryHandle getQueryHandle(String queryHandle) {
        try {
            return QueryHandle.fromString(queryHandle);
        } catch (Exception e) {
            throw new BadRequestException("Invalid query handle: "  + queryHandle, e);
        }
    }

    /**
     * Get all the queries in the query server; can be filtered with state and user.
     *
     * @param publicId The public id of the session in which user is working
     * @param state If any state is passed, all the queries in that state will be returned,
     * otherwise all queries will be returned. Possible states are {@value QueryStatus.Status#values()}
     * @param user If any user is passed, all the queries submitted by the user will be returned,
     * otherwise all the queries will be returned
     *
     * @return List of {@link QueryHandle} objects
     */
    @GET
    @Path("queries")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
    public List<QueryHandle> getAllQueries(@QueryParam("publicId") UUID publicId,
                                           @DefaultValue("") @QueryParam("state") String state,
                                           @DefaultValue("") @QueryParam("user") String user) {
        GrillSessionHandle sessionHandle = SessionUIResource.openSessions.get(publicId);
        checkSessionHandle(sessionHandle);
        try {
            return queryServer.getAllQueries(sessionHandle, state, user);
        } catch (GrillException e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * Submit the query for explain or execute or execute with a timeout
     *
     * @param publicId The public id of the session in which user is submitting the query. Any
     *  configuration set in the session will be picked up.
     * @param query The query to run
     *
     * @return {@link QueryHandle}
     */
    @POST
    @Path("queries")
    @Consumes({MediaType.MULTIPART_FORM_DATA})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
    public QuerySubmitResult query(@FormDataParam("publicId") UUID publicId,
                                   @FormDataParam("query") String query) {
        GrillSessionHandle sessionHandle = SessionUIResource.openSessions.get(publicId);
        checkSessionHandle(sessionHandle);
        GrillConf conf;
        checkQuery(query);
        try {
            conf = new GrillConf();
            return queryServer.executeAsync(sessionHandle, query, conf);
        } catch (GrillException e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * Get grill query and its current status
     *
     * @param publicId The public id of session handle
     * @param queryHandle The query handle
     *
     * @return {@link GrillQuery}
     */
    @GET
    @Path("queries/{queryHandle}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
    public GrillQuery getStatus(@QueryParam("publicId") UUID publicId,
                                @PathParam("queryHandle") String queryHandle) {
        GrillSessionHandle sessionHandle = SessionUIResource.openSessions.get(publicId);
        checkSessionHandle(sessionHandle);
        try {
            return queryServer.getQuery(sessionHandle,
                    getQueryHandle(queryHandle));
        } catch (GrillException e) {
            throw new WebApplicationException(e);
        }
    }


    @GET
    @Path("queries/{queryHandle}/resultsetmetadata")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
    public QueryResultSetMetadata getResultSetMetadata(
            @QueryParam("publicId") UUID publicId,
            @PathParam("queryHandle") String queryHandle) {
        GrillSessionHandle sessionHandle = SessionUIResource.openSessions.get(publicId);
        checkSessionHandle(sessionHandle);
        try {
            return queryServer.getResultSetMetadata(sessionHandle, getQueryHandle(queryHandle));
        } catch (GrillException e) {
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Path("queries/{queryHandle}/resultset")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
    public ResultRow getResultSet(
            @QueryParam("publicId") UUID publicId,
            @PathParam("queryHandle") String queryHandle,
            @QueryParam("pageNumber") int pageNumber,
            @QueryParam("fetchsize") int fetchSize) {
        GrillSessionHandle sessionHandle = SessionUIResource.openSessions.get(publicId);
        List<Object> rows = new ArrayList<Object>();
        checkSessionHandle(sessionHandle);

        try {
            LOG.info("FetchResultSet:" + sessionHandle.toString() + " query:" + queryHandle);
            QueryResultSetMetadata resultSetMetadata = queryServer.getResultSetMetadata(sessionHandle, getQueryHandle(queryHandle));
            List<ResultColumn> metaColumns = resultSetMetadata.getColumns();
            List<Object> metaResultColumns = new ArrayList<Object>();
            for(ResultColumn column : metaColumns) {
                metaResultColumns.add(column.getName());
            }
            rows.add(new ResultRow(metaResultColumns));


            QueryResult result = queryServer.fetchResultSet(sessionHandle, getQueryHandle(queryHandle), (pageNumber - 1) * fetchSize,
                    fetchSize);
            if(result instanceof InMemoryQueryResult) {
                InMemoryQueryResult inMemoryQueryResult = (InMemoryQueryResult) result;
                List<ResultRow> tableRows = inMemoryQueryResult.getRows();
                rows.addAll(tableRows);
            }
            else if(result instanceof PersistentQueryResult)
            {
                PersistentQueryResult persistentQueryResult = (PersistentQueryResult) result;
                LOG.info("Persisted File URI: " + persistentQueryResult.getPersistedURI());
                try {
                    LineNumberReader resultFile = null;
                    try {
                        resultFile = new LineNumberReader(new FileReader(persistentQueryResult.getPersistedURI().substring(5) + "/000000_0"));
                        String str;
                        while ((str = resultFile.readLine()) != null) {
                            //resultColumns = new ArrayList<String>(Arrays.asList(str.split("[\u0001]")));
                            List<Object> columns = new ArrayList<Object>();
                            String[] strArray = str.split('\u0001' + "");
                            for(String strColumn:strArray)
                                columns.add(strColumn);

                            rows.add(new ResultRow(columns));
                            LOG.info("Read from FILE : " + str);
                        }
                    } catch (Exception e) {
                        throw new WebApplicationException(e);
                    } finally {

                        // closes the stream and releases system resources
                        if (resultFile != null)
                            resultFile.close();
                    }
                } catch (IOException e) {
                    throw new WebApplicationException(e);
                }
            }
            //Response response = queryServer.getHttpResultSet(sessionHandle, getQueryHandle(queryHandle));

            return new ResultRow(rows);
        } catch (GrillException e) {
            throw new WebApplicationException(e);
        }
    }
}
