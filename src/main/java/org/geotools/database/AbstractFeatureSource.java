package org.geotools.database;

import org.geotools.api.data.DataSourceException;
import org.geotools.api.data.DataStore;
import org.geotools.api.feature.type.Name;
import org.geotools.api.filter.Filter;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.DefaultFeatureResults;
import org.geotools.data.Diff;
import org.geotools.api.data.Query;
import org.geotools.api.data.QueryCapabilities;
import org.geotools.api.data.ResourceInfo;
import org.geotools.api.data.Transaction;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Envelope;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.data.store.EmptyFeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.logging.Logging;
import org.geotools.api.feature.simple.SimpleFeatureType;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

public abstract class AbstractFeatureSource implements SimpleFeatureSource {
    private static final Logger LOGGER = Logging.getLogger("org.geotools.data");
    protected Set hints;
    protected QueryCapabilities queryCapabilities;

    public AbstractFeatureSource() {
        this.hints = Collections.EMPTY_SET;
        this.queryCapabilities = new QueryCapabilities();
    }

    public abstract DataStore getDataStore();

    public Name getName() {
        return this.getSchema().getName();
    }

    public AbstractFeatureSource(Set hints) {
        this.hints = Collections.EMPTY_SET;
        this.hints = Collections.unmodifiableSet(new HashSet(hints));
        this.queryCapabilities = new QueryCapabilities() {
            public boolean isUseProvidedFIDSupported() {
                return AbstractFeatureSource.this.hints.contains(Hints.USE_PROVIDED_FID);
            }
        };
    }

    public ResourceInfo getInfo() {
        return new ResourceInfo() {
            final Set<String> words = new HashSet<>();

            {
                this.words.add("features");
                this.words.add(AbstractFeatureSource.this.getSchema().getTypeName());
            }

            public ReferencedEnvelope getBounds() {
                try {
                    return AbstractFeatureSource.this.getBounds();
                } catch (IOException var2) {
                    return null;
                }
            }

            public CoordinateReferenceSystem getCRS() {
                return AbstractFeatureSource.this.getSchema().getCoordinateReferenceSystem();
            }

            public String getDescription() {
                return null;
            }

            public Set<String> getKeywords() {
                return this.words;
            }

            public String getName() {
                return AbstractFeatureSource.this.getSchema().getTypeName();
            }

            public URI getSchema() {
                Name name = AbstractFeatureSource.this.getSchema().getName();

                try {
                    return new URI(name.getNamespaceURI());
                } catch (URISyntaxException var4) {
                    return null;
                }
            }

            public String getTitle() {
                Name name = AbstractFeatureSource.this.getSchema().getName();
                return name.getLocalPart();
            }
        };
    }

    public QueryCapabilities getQueryCapabilities() {
        return this.queryCapabilities;
    }

    public Transaction getTransaction() {
        return Transaction.AUTO_COMMIT;
    }

    public SimpleFeatureCollection getFeatures(Query query) throws IOException {
        SimpleFeatureType schema = this.getSchema();
        String typeName = schema.getTypeName();
        if (query.getTypeName() == null) {
            Query defaultQuery = new Query(query);
            defaultQuery.setTypeName(typeName);
        } else if (!typeName.equals(query.getTypeName())) {
            return new EmptyFeatureCollection(schema);
        }

        QueryCapabilities queryCapabilities = this.getQueryCapabilities();
        if (!queryCapabilities.supportsSorting(query.getSortBy())) {
            throw new DataSourceException("DataStore cannot provide the requested sort order");
        } else {
            SimpleFeatureCollection collection = new DefaultFeatureResults(this, query);
            return collection.getSchema().getGeometryDescriptor() == null ? collection : collection;
        }
    }

    public SimpleFeatureCollection getFeatures(Filter filter) throws IOException {
        return this.getFeatures(new Query(this.getSchema().getTypeName(), filter));
    }

    public SimpleFeatureCollection getFeatures() throws IOException {
        return this.getFeatures(Filter.INCLUDE);
    }

    public ReferencedEnvelope getBounds() throws IOException {
        return this.getBounds((this.getSchema() == null ? Query.ALL : new Query(this.getSchema().getTypeName())));
    }

    public ReferencedEnvelope getBounds(Query query) throws IOException {
        if (query.getFilter() == Filter.EXCLUDE) {
            return new ReferencedEnvelope(new Envelope(), this.getSchema().getCoordinateReferenceSystem());
        } else {
            DataStore dataStore = this.getDataStore();
            return dataStore instanceof AbstractDataStore ? ((AbstractDataStore)dataStore).getBounds(this.namedQuery(query)) : null;
        }
    }

    protected Query namedQuery(Query query) {
        String typeName = this.getSchema().getTypeName();
        return query.getTypeName() != null && query.getTypeName().equals(typeName) ? query : new Query(typeName, query.getFilter(), query.getMaxFeatures(), query.getPropertyNames(), query.getHandle());
    }

    public int getCount(Query query) throws IOException {
        if (query.getFilter() == Filter.EXCLUDE) {
            return 0;
        } else {
            DataStore dataStore = this.getDataStore();
            if (dataStore instanceof AbstractDataStore) {
                Transaction t = this.getTransaction();
                int nativeCount = ((AbstractDataStore)dataStore).getCount(this.namedQuery(query));
                if (nativeCount == -1) {
                    return -1;
                } else {
                    int delta = 0;
                    if (t != Transaction.AUTO_COMMIT) {
                        if (t.getState(dataStore) == null) {
                            return nativeCount;
                        }

                        if (!(t.getState(dataStore) instanceof TransactionStateDiff)) {
                            return -1;
                        }

                        Diff diff = ((AbstractDataStore)dataStore).state(t).diff(this.namedQuery(query).getTypeName());
                        synchronized(diff) {
                            Iterator it = diff.getAdded().values().iterator();

                            Object feature;
                            while(it.hasNext()) {
                                feature = it.next();
                                if (query.getFilter().evaluate(feature)) {
                                    ++delta;
                                }
                            }

                            it = diff.getModified().values().iterator();

                            while(it.hasNext()) {
                                feature = it.next();
                                if (feature == Diff.NULL && query.getFilter().evaluate(feature)) {
                                    --delta;
                                }
                            }
                        }
                    }

                    return nativeCount + delta;
                }
            } else {
                return -1;
            }
        }
    }

    public Set getSupportedHints() {
        return this.hints;
    }
}
