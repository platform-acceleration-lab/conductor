# Netflix Conductor Fork

Forked from [Netflix Conductor](https://github.com/Netflix/conductor)

## Fork status

- Most recent Netflix release is v2.25.5 (Feb 15, 2020)
- Our branch is tracking release v2.12.5 (Jun 6, 2019) - We are behind

Here is a rundown of the most important of our additions and changes:
- Rolls over task_log indices yearly rather than weekly 

  This is to accommodate a smaller Elasticsearch tile instance
  (each task_log index counts against the number of indexes
  quota in the Elasticsearch plan).
  
- Handle re-indexing either Redis or MySQL at startup

  This is only necessary if using in-memory Elasticsearch, which we do
  for local development, but is not required unless you need to persist
  state across restarts.
  
- Upgrade npm to v10.15

  This is required if we continue to use the Node buildpack on PWS.
  The current buildpack (not the one that was there when we last deployed
  Conductor) requires node version 10.18, which means that
  the next time we deploy Conductor we would need to either:
  - upgrade the node version, or
  - use a previous buildpack version that supports the version of Node.
  
- Use Elasticsearch 6 instead of 5

  This is required because our Elasticsearch provider only supports HTTP
  connections (as opposed to Elasticsearch's "native" TCP protocol),
  which requires ES6.
  "Our" changes are mostly just following the instructions
  on the Conductor site.
  
- Support basic authentication over ES6 REST client

  We do this in order to be able to bo basic auth to Elasticsearch,
  which is easier than other alternatives.
