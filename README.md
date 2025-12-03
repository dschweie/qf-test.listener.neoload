# qf-test.listener.neoload
QF-Test and NeoLoad are testing tools that serve different purposes. Thanks 
to the powerful interfaces of QF-Test and NeoLoad, the two products can also 
be used together. 

In order to be able to use test cases developed in QF-Test in a performance 
test, QF-Test can be called from NeoLoad using suitable custom actions. So that 
the duration of the individual test cases and sequences can be evaluated in 
NeoLoad, QF-Test must report these metrics back to NeoLoad. The 
AbstractNeoloadDataExchangeListener implements the essential functions for this 
task.

Reusing existing test cases has two main advantages:
1. The times for developing suitable test cases with NeoLoad are shortened and 
testing can therefore be carried out earlier.
2. While the NeoLoad measurements usually only provide information about the 
response time, the QF-Test measurements include not only the response time but 
also the time for updating or building the page. This often named as 
end-user-experience, which can differ greatly from the response time at 
the protocol level.


## Required Software

The software enables the exchange of information between the software products 
QF-Test and NeoLoad and therefore cannot be used independently.

### QF-Test
[QF-Test](https://www.qfs.de/en/product/qf-test.html) is commercial software 
from Quality First Software for automatically testing programs via their 
graphical user interface. 

It must be provided with a proprietary license in order to use the listener.

The listener can be used from QF-Test 4.6.1.

### NeoLoad
[NeoLoad](https://www.tricentis.com/products/performance-testing-neoload) is a 
performance testing tool from Tricentis. NeoLoad primarily supports the common 
protocols used by modern web applications to measure response times.

In order for NeoLoad to receive data from QF-Test, the Data Exchange API 
function in NeoLoad must be licensed.

The listener can be used from NeoLoad 6.1.

### Custom Actions for QF-Test
Executing any program from NeoLoad is generally possible with the 
“Executable Test Script” action. In the case of QF-Test this would be possible 
because QF-Test can pass many configuration settings via the command line.

The task is a little easier with specialized custom actions, which take care of 
the corresponding call with just a few parameters. The repository 
[neoload.advanced-action.qf-test](https://github.com/dschweie/neoload.advanced-action.qf-test) 
contains these custom actions, which can then be integrated and used in the project.

##  Possible initial steps

This repository contains a showcase that allows you to test it in your local environment after a short time. The necessary steps are documented on the [following page](doc/howto_showcase.md).