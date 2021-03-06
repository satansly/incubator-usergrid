/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.usergrid.management.export;


import java.io.File;
import java.util.Map;
import java.util.Properties;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.AsyncBlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobBuilder;
import org.jclouds.blobstore.options.PutOptions;
import org.jclouds.http.config.JavaUrlHttpCommandExecutorServiceModule;
import org.jclouds.logging.log4j.config.Log4JLoggingModule;
import org.jclouds.netty.config.NettyPayloadModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Module;


/**
 *
 *
 */
public class S3ExportImpl implements S3Export {

    String fn;

    @Override
    public void copyToS3( File ephemeral ,final Map<String,Object> exportInfo, String filename ) {

        fn = filename;

        Logger logger = LoggerFactory.getLogger( ExportServiceImpl.class );
        /*won't need any of the properties as I have the export info*/
        Map<String,Object> properties = ( Map<String, Object> ) exportInfo.get( "properties" );

        Map<String, Object> storage_info = (Map<String,Object>)properties.get( "storage_info" );

        String bucketName = ( String ) storage_info.get( "bucket_location" );
        String accessId = ( String ) storage_info.get( "s3_access_id" );
        String secretKey = ( String ) storage_info.get( "s3_key" );

        Properties overrides = new Properties();
        overrides.setProperty( "s3" + ".identity", accessId );
        overrides.setProperty( "s3" + ".credential", secretKey );

        final Iterable<? extends Module> MODULES = ImmutableSet
                .of( new JavaUrlHttpCommandExecutorServiceModule(), new Log4JLoggingModule(),
                        new NettyPayloadModule() );

        BlobStoreContext context =
                ContextBuilder.newBuilder( "s3" ).credentials( accessId, secretKey ).modules( MODULES )
                              .overrides( overrides ).buildView( BlobStoreContext.class );

        // Create Container (the bucket in s3)
        try {
            AsyncBlobStore blobStore = context.getAsyncBlobStore(); // it can be changed to sync
            // BlobStore (returns false if it already exists)
            ListenableFuture<Boolean> container = blobStore.createContainerInLocation( null, bucketName );
            if ( container.get() ) {
                logger.info( "Created bucket " + bucketName );
            }
        }
        catch ( Exception ex ) {
            logger.error( "Could not start binary service: {}", ex.getMessage() );
            return;
        }

        try {
            AsyncBlobStore blobStore = context.getAsyncBlobStore();
            BlobBuilder blobBuilder =
                    blobStore.blobBuilder( fn ).payload( ephemeral ).calculateMD5().contentType( "application/json" );


            Blob blob = blobBuilder.build();

            ListenableFuture<String> futureETag = blobStore.putBlob( bucketName, blob, PutOptions.Builder.multipart() );


            logger.info( "Uploaded file etag=" + futureETag.get() );
        }
        catch ( Exception e ) {
            logger.error( "Error uploading to blob store", e );
        }
    }

    @Override
    public String getFilename () {return fn;}

}
