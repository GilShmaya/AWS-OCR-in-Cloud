# AWS-OCR-in-Cloud
Submitters:
- Gil Shmaya 209327303
- Yael Elad 318917622


Project details :
- AMI - "ami-07b0cb5b5abb9dff7"
- Instance type  - t2.micro
- IAM_Role - "arn:aws:iam::262099114720:instance-profile/LabInstanceProfile"
- Total time to finish working on the input files : 
#### Running the program :


#### Program flow :
##### local application  :
 
1. Local App search for an active Manager (EC2 instance) :
2. if the manager exist, an sqs queue for the local's messages to the manager was already initialized, ask for its url.
3. else, local application initialize the manager (a new EC2 instance) and a new sqs queue - local app messages to manager.
4. Local App upload the input file to s3.
5. Local App creates a new (and personal) sqs queue - through which it can receives messages from the manager (ManagerToLocalSQS).
6. Local App sends a message to the manager, that includes all necessary information in order to perform the task (including location of input file in s3).
7. Local App waits for the manager's response.
8. Local App generate html output from response she got, deleting the personal sqs queue with the manager.
9. Local App checks is a termination message occured - if it does, sends a terminate message to the manager.



##### Manager :
1. Manager creates a new s3.
2. Manager creates a new sqs queue for the manager's messages to the workers - managerToWorkersSQS.
3. Manager creates a new sqs queue for the workers's messages to wothe manager - workersToManagerSQS.
4. Manager starts and run two threads - one for his communication with the local application (AppManagerContact) and one for the workers (ManagerWorkersContact) : 

* AppManagerContact (AMC) :

1. AMC Create new sqs queue (localToManagerSQS) and runs a new thread - WorkersThred : a thread that checks if all active workers are working all the time. 
2. AMC runs in while loop (until a terminate message sent by local app) checking if a new message arrived from the local application (through localToManagerSQS).
3. if a termination message arrives - AMC activates the function Terminate (waiting for all activates workers to finish and then terminate).
4. if a task message arrives- AMC crate a new task - get the information from the message, (key, bucket and n (the ratio of workers-images))
5. AMC download the input file from S3
6. AMC create an OCR file with the number of urls in the input message & send it to s3.
7. AMC create an empty file for the worker's result.
8. AMC send the task to the workers (through managerToWorkersSQS) mention the amount of workers that are necessary in order to do the task.
9. AMC delete the file from s3 and the message from localToManagerSQS. 

* ManagerWorkersContact (MWC):

1. MWC creates a new sqs queue - workersToManagerSQS.
2. MWC runs in a while loop (until terminate status in data base) - checks if a new message arrived from the workers.
3. if a "finish" message arrives (summary file) :
4. MWC process the text that was sent by the workers in the summaryfile message.
5. MWC send the processed text to database.
6. MWC removes the task from database.
7. MWC send a "done" message to the local application through the local sqs (containing the results and the message details).
8. else -MWC exit- missing information in the message. 
9. MWC delete the processed message from workerToManagerQ. 


##### Workers :

1. As the Workers was activated in the DataBase (by the AppManagerContact thread) it creates two sqs queues:
2. workersToManagerSQS - where the workers can send messages to the manager.
3. managerToWorkersSQS - where the manager can send messages to the workers.
4. Workers runs in a while loop, as long as the data base wasn't terminated yet - each worker does the folloing :
5. worker check if a new message arrived from the manager (in managerToWorkersSQS).
6. once a new message arrived - worker gets the information from the message.
7. worker tries to apply OCR on the given URL.
8. if the OCR process was successful -worker creats a summary mesage that includes the string he got from applying the ocr on the image.
9. if the OCR process wasn't successful - worker creats a summary mesage indicates that the process failed.
10. worker send the summary message to the manager (through managerGetFromWorkers SQS).
11. worker delete the current message from the workersGetFromManager SQS. 



###### Security :

###### Scalability: 

The buttle neck of this project is the Manager, so in order to deal with the scalability, we used three threads to divide his work. 
The AppManagerContact and the ManagerWorkersContact are constantly checking their SQS waiting for new information - and therefor the Manager never has to wait. 
Moreover, we make little use of the manager's memory

###### Persistence:

As long as a worker works on a task, the message he is dealing with stays in the main SQS, and only after finish processing the task, it will be deleted. That way, in case a node (worker) cant finish his job, another worker will take it. 
In order to handle the workers activity, we created another thread - WorkersThread - that constantly checks if all active workers are working. In case a termination of a worker, he will be removed from database and a new worker will be added instead. 
In some cases, the worker's job might take a while (a node stalls for a while), so in order to deal with this problem and let the worker finish his task we extended the visibility timeout.

