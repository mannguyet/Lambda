FROM public.ecr.aws/lambda/java:8

# Copy function code and runtime dependencies from Maven layout
COPY target/classes ${LAMBDA_TASK_ROOT}
COPY target/dependency/* ${LAMBDA_TASK_ROOT}/lib/

# Set the CMD to your handler (could also be done as a parameter override outside of the Dockerfile)
CMD [ "pkg.Lambda::handleRequest" ]


docker build -t java04 .

docker run -p 9000:8080 java04
curl -XPOST "http://192.168.99.100:9000/2015-03-31/functions/function/invocations" -d '{"payload":"hello world!"}'