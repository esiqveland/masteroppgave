# plotter.R

args<-commandArgs(TRUE)

library('getopt') 

spec <- matrix(c(
        'zkdata'     , 'z', 1, "character", "zookeeperdata input file csv (required)",
        'official'     , 'i', 1, "character", "input csv file for official version data (required)",
        'output'    , 'o', 1, "character", "output filename (required)",
        'help'   , 'h', 0, "logical",   "this help"
),ncol=5,byrow=T)

opt = getopt(spec);

if ( !is.null(opt$help) || is.null(opt$official) || is.null(opt$zkdata) || is.null(opt$out) ) {
    cat(paste(getopt(spec, usage=T),"\n"));
    q(status=1);
}

#setup output
pdf(opt$out)

mydata = read.csv(opt$zkdata)
# unpack histogram
zookeeperdata <- unlist(apply(mydata, 1, function(x) rep(x[1], x[2])))


mydatanozk = read.csv(opt$official)
# unpack histogram
nozk <- unlist(apply(mydatanozk, 1, function(x) rep(x[1], x[2])))

quantile(nozk, prob=c(0.9, 0.999))
quantile(zookeeperdata, prob=c(0.9, 0.999))



responsetime = zookeeperdata#$responsetime

par(xlog=FALSE)
Fn = ecdf(responsetime)

summary(nozk)
sd(nozk)
summary(zookeeperdata)
sd(zookeeperdata)
xrange = sd(nozk)^2 + 2*mean(nozk) + 45

#plot(Fn, main="Response times", xlab="response time (ms)", ylab="cumulative response proportion", xlim=c(1,500), log="x")
#plot(Fn, main="Response times", xlab="response time (ms)", ylab="cumulative response proportion", xlim=c(1,500), log="x")

library(Hmisc)
#g <- c( rep('Response Time (ms)',length(responsetime)) )
x <- c(nozk, zookeeperdata)
g <- c(rep('No ZooKeeper',length(nozk)),rep('ZooKeeper',length(zookeeperdata)))

Ecdf(x, group=g, 
	main="Response time distribution", 
	xlab='Response Time (ms) ',
	ylab = "percentile of requests",
	datadensity="none", 
	col=c('blue', 'red'),
	lty=c(2,1),
	lwd=c(2,1),
	label.curves=list(keys=1:2),
	#log="x",
	q=c(.90,.999),
	xlim=range(0,xrange)
	)

# second drawing
# Ecdf(originalnozk, group=g, 
# 	main="Response time distribution", 
# 	xlab='Response Time ms ',
	# ylab = "percentile of requests",
	# datadensity="none", 
	# add=TRUE,
	# label.curves=TRUE,
	# col='blue',
	# #log="x",
	# q=c(.50,.90,.999),
	# xlim=range(0,5)
	# )
